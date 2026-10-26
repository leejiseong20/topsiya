package com.topsiya.goal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.topsiya.lane.LaneReport;
import com.topsiya.lane.LaneReport.Checkpoint;
import com.topsiya.lane.LaneReport.Death;
import com.topsiya.lane.LaneService;
import com.topsiya.riot.RiotApiClient;
import com.topsiya.riot.dto.MatchDto;
import com.topsiya.store.FileStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoalTest {

    private static final String ME = "me";

    @TempDir
    Path dir;

    // ---------- 지표 계산 ----------

    @Test
    void 와드_없이_죽은_횟수는_15분_전_데스만_센다() {
        LaneReport report = report("KR_1", 1800,
                death(300, 0, false), death(700, 2, false), death(850, 0, true), death(1000, 0, false));

        assertThat(GoalMetric.WARDLESS_DEATHS_BEFORE_15.measure(report)).isEqualTo(2);
        assertThat(GoalMetric.DEATHS_BEFORE_15.measure(report)).isEqualTo(3);
        assertThat(GoalMetric.JUNGLE_GANK_DEATHS_BEFORE_15.measure(report)).isEqualTo(1);
    }

    @Test
    void 십오분_전에_끝난_경기는_판정하지_않는다() {
        assertThat(GoalMetric.DEATHS_BEFORE_15.measure(report("KR_1", 840))).isNull();
    }

    @Test
    void CS_차이는_그_시점_체크포인트를_쓴다() {
        LaneReport report = report("KR_1", 1800);

        assertThat(GoalMetric.CS_DIFF_AT_10.measure(report)).isEqualTo(7);
        assertThat(GoalMetric.CS_DIFF_AT_15.measure(report)).isNull(); // 15분 체크포인트 없음
    }

    @Test
    void 데스는_이하_CS는_이상이면_성공() {
        assertThat(GoalMetric.DEATHS_BEFORE_15.achieved(1, 1)).isTrue();
        assertThat(GoalMetric.DEATHS_BEFORE_15.achieved(2, 1)).isFalse();
        assertThat(GoalMetric.CS_DIFF_AT_10.achieved(5, 5)).isTrue();
        assertThat(GoalMetric.CS_DIFF_AT_10.achieved(4, 5)).isFalse();
    }

    @Test
    void 기준_문장() {
        assertThat(GoalMetric.WARDLESS_DEATHS_BEFORE_15.describe(0)).isEqualTo("15분 전 와드 없이 죽은 횟수 0번 이하");
        assertThat(GoalMetric.CS_DIFF_AT_10.describe(5)).isEqualTo("10분 CS 차이 +5개 이상");
        assertThat(GoalMetric.WARDS_BEFORE_15.describe(6)).isEqualTo("15분까지 설치한 와드 6개 이상");
    }

    // ---------- 목표 저장·판정 ----------

    @Test
    void 목표를_정한_뒤에_한_같은_포지션_경기만_판정한다() {
        RiotApiClient riot = mock(RiotApiClient.class);
        LaneService lane = mock(LaneService.class);
        // 최신순: KR_4(목표 후, TOP), KR_3(목표 후, MID), KR_2(목표 후, TOP), KR_1(목표 만든 경기)
        when(riot.getMatchIds(eq(ME), anyInt())).thenReturn(List.of("KR_4", "KR_3", "KR_2", "KR_1"));
        when(riot.getMatch("KR_1")).thenReturn(match("KR_1", 1_000, "TOP"));
        when(riot.getMatch("KR_2")).thenReturn(match("KR_2", 2_000, "TOP"));
        when(riot.getMatch("KR_3")).thenReturn(match("KR_3", 3_000, "MIDDLE"));
        when(riot.getMatch("KR_4")).thenReturn(match("KR_4", 4_000, "TOP"));
        when(lane.analyze("KR_2", ME)).thenReturn(report("KR_2", 1800, death(300, 0, false)));
        when(lane.analyze("KR_4", ME)).thenReturn(report("KR_4", 1800, death(300, 3, false)));

        GoalService service = new GoalService(new FileStore(dir, new ObjectMapper()), riot, lane);
        GoalProgress progress = service.set(ME, new GoalRequest(
                GoalMetric.WARDLESS_DEATHS_BEFORE_15, 0, "와드 없이 강 넘지 않기", "TOP", "KR_1"));

        assertThat(progress.goal().since()).isEqualTo(1_000);
        assertThat(progress.criterion()).isEqualTo("15분 전 와드 없이 죽은 횟수 0번 이하");
        assertThat(progress.checks()).extracting(GoalProgress.Check::matchId).containsExactly("KR_4", "KR_2");
        assertThat(progress.checks()).extracting(GoalProgress.Check::achieved).containsExactly(true, false);
        assertThat(progress.achievedCount()).isEqualTo(1);
    }

    @Test
    void 목표가_없으면_빈값_그만두면_사라진다() {
        RiotApiClient riot = mock(RiotApiClient.class);
        when(riot.getMatchIds(eq(ME), anyInt())).thenReturn(List.of());
        GoalService service = new GoalService(new FileStore(dir, new ObjectMapper()), riot, mock(LaneService.class));

        assertThat(service.progress(ME)).isEmpty();

        service.set(ME, new GoalRequest(GoalMetric.DEATHS_BEFORE_15, 1, "덜 죽기", "", null));
        assertThat(service.progress(ME)).isPresent();

        service.clear(ME);
        assertThat(service.progress(ME)).isEmpty();
    }

    // ---------- 테스트 데이터 ----------

    private static Death death(long seconds, int wards, boolean jungle) {
        return new Death(String.format("%d:%02d", seconds / 60, seconds % 60), seconds, "Vi", List.of(),
                jungle ? "정글 갱" : "라인 교전", jungle, false, "강가", wards);
    }

    private static LaneReport report(String matchId, long duration, Death... deaths) {
        return new LaneReport(matchId, "TOP", true, duration, "Darius", "Garen",
                List.of(new Checkpoint(5, 40, 38, 2, 50, 0), new Checkpoint(10, 80, 73, 7, 150, 0)),
                List.of(), null, List.of(deaths), 0, 0, 0, 0, null, null, 5);
    }

    private static MatchDto match(String matchId, long endTimestamp, String position) {
        MatchDto.Participant me = new MatchDto.Participant(1, ME, "Darius", position, 100, true,
                1, 1, 1, 100, 0, 10);
        return new MatchDto(new MatchDto.Metadata(matchId, List.of(ME)),
                new MatchDto.Info(1800, endTimestamp, 420, List.of(me)));
    }
}
