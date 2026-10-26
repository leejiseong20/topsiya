package com.topsiya.coach;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.topsiya.lane.LaneReport;
import com.topsiya.lane.LaneReport.Checkpoint;
import com.topsiya.lane.LaneReport.Death;
import com.topsiya.lane.LaneReport.MinutePoint;
import com.topsiya.verify.Verification;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoachContextCheckTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 데스: 4:10 정글 갱(상대 진영, 와드 0) / 9:45 라인 교전(우리 진영, 와드 2). 15분까지 와드 8개, 방패 획득 5·잃음 2 */
    private static LaneReport lane() {
        List<MinutePoint> timeline = new ArrayList<>();
        int[] diffs = {0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 3, 1, -1, -3, -5};
        for (int m = 0; m < diffs.length; m++) {
            timeline.add(new MinutePoint(m, m * 8 + diffs[m], m * 8, diffs[m], diffs[m] * 30));
        }
        return new LaneReport("KR_1", "TOP", false, 1800, "Darius", "Garen",
                List.of(new Checkpoint(5, 42, 40, 2, 60, 0), new Checkpoint(10, 85, 80, 5, 150, 0),
                        new Checkpoint(15, 115, 120, -5, -150, 0)),
                timeline, new LaneReport.Swing(10, 13, -6),
                List.of(new Death("4:10", 250, "LeeSin", List.of("Garen"), "정글 갱", true, false, "상대 진영", 0),
                        new Death("9:45", 585, "Garen", List.of(), "라인 교전", false, false, "우리 진영", 2)),
                0, 1, 5, 2, null, null, 8);
    }

    private static CoachingReport report(String summary, String what, String why, String good) {
        return new CoachingReport(summary,
                List.of(new CoachingReport.Mistake("4:10", what, why, "와드를 먼저 박으세요.")),
                List.of(good), "다음엔 와드부터", null, null, null);
    }

    private Verification verify(CoachingReport r) {
        return CoachEvidence.verify(lane(), r, objectMapper);
    }

    @Test
    void 맞는_답은_전부_통과() {
        Verification v = verify(report(
                "10분까지 CS 차이를 5까지 벌렸지만, 15분 전 정글 갱으로 1번 사망했고 9:45에는 우리 진영 라인 교전에서 죽었습니다.",
                "상대 진영에서 와드 없이 버티다 정글 갱을 당했습니다.",
                "4:10 사망 직전 2분 와드는 0개였고 위치는 상대 진영이었습니다.",
                "15분까지 와드를 8개 설치했고 포탑 방패를 5개 획득했습니다."));

        assertThat(v.unmatched()).isEmpty();
        assertThat(v.contextClaims()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void 시각은_맞아도_데스_유형이_다르면_잡는다() {
        Verification v = verify(report("", "라인 교전에서 밀려 죽었습니다.", "4:10 라인 교전 사망.", ""));

        assertThat(v.unmatched()).anyMatch(u -> u.contains("라인 교전이 아니다"));
    }

    @Test
    void 위치와_직전_와드_수가_다르면_잡는다() {
        Verification v = verify(report("", "우리 진영에서 잡혔습니다.", "4:10 사망 직전 2분 와드는 3개였습니다.", ""));

        assertThat(v.unmatched()).anyMatch(u -> u.contains("위치는 '우리 진영'이 아니라 '상대 진영'"));
        assertThat(v.unmatched()).anyMatch(u -> u.contains("와드는 3개가 아니라 0개"));
    }

    @Test
    void 이름표_붙은_숫자는_같은_종류_값과_맞아야_한다() {
        // 와드 5개(실제 8), 방패 획득 2개(실제 5, 잃은 수가 2) → 둘 다 데이터 어딘가에 있는 숫자지만 틀린 말
        Verification v = verify(report("", "정글 갱", "4:10 정글 갱", "15분까지 와드를 5개 설치했고 포탑 방패를 2개 획득했습니다."));

        assertThat(v.unmatched()).anyMatch(u -> u.contains("와드는 5개가 아니라 8개"));
        assertThat(v.unmatched()).anyMatch(u -> u.contains("포탑 방패 2개"));
    }

    @Test
    void 요약에_여러_사건이_섞여도_구절별로_본다() {
        // "정글 갱"은 앞 구절(4:10) 이야기 → 9:45 라인 교전 데스를 정글 갱이라고 오해하지 않는다
        Verification v = verify(report("15분 전 정글 갱으로 1번 사망했고 9:45에는 라인 교전에서도 죽었습니다.",
                "정글 갱", "4:10 정글 갱", ""));

        assertThat(v.unmatched()).isEmpty();
    }

    @Test
    void 유형별_데스_수도_인정한다() {
        assertThat(CoachContextCheck.deathCounts(lane())).contains(1, 2);
    }

    @Test
    void 검사기_시험_심은_실수를_엄격한_검사는_잡고_느슨한_검사는_놓친다() {
        CoachingReport good = report(
                "15분 전 2데스를 기록했고, 15분 CS 차이는 -5였습니다.",
                "상대 진영에서 정글 갱을 당했습니다.",
                "4:10 사망 직전 2분 와드는 0개였습니다.",
                "15분까지 와드를 8개 설치했습니다.");

        List<CoachProbe.Result> results = CoachProbe.run(lane(), good, objectMapper);

        assertThat(results).extracting(CoachProbe.Result::type)
                .contains("시각", "데스 유형", "데스 위치", "와드 수", "CS 숫자", "데스 수");
        assertThat(results).allMatch(CoachProbe.Result::caught);
        assertThat(results).filteredOn(r -> r.type().equals("데스 유형")).noneMatch(CoachProbe.Result::caughtLoose);
    }
}
