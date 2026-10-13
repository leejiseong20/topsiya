package com.topsiya.lane;

import com.topsiya.riot.dto.MatchDto;
import com.topsiya.riot.dto.MatchDto.Participant;
import com.topsiya.riot.dto.TimelineDto;
import com.topsiya.riot.dto.TimelineDto.Event;
import com.topsiya.riot.dto.TimelineDto.Frame;
import com.topsiya.riot.dto.TimelineDto.ParticipantFrame;
import com.topsiya.riot.dto.TimelineDto.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LaneAnalyzerTest {

    static final String ME = "me-puuid";

    @Test
    void 체크포인트와_CS격차를_계산한다() {
        LaneReport report = LaneAnalyzer.analyze(sampleMatch("TOP"), sampleTimeline(), ME);

        assertThat(report.myChampion()).isEqualTo("Darius");
        assertThat(report.opponentChampion()).isEqualTo("Kennen");
        assertThat(report.checkpoints()).extracting(LaneReport.Checkpoint::minute).containsExactly(5, 10, 15);

        LaneReport.Checkpoint at10 = report.checkpoints().get(1);
        assertThat(at10.myCs()).isEqualTo(80);
        assertThat(at10.opponentCs()).isEqualTo(70);
        assertThat(at10.csDiff()).isEqualTo(10);

        LaneReport.Checkpoint at15 = report.checkpoints().get(2);
        assertThat(at15.myCs()).isEqualTo(108);
        assertThat(at15.opponentCs()).isEqualTo(114);
        assertThat(at15.csDiff()).isEqualTo(-6);    // 12분부터 밀리기 시작해서 역전
        assertThat(report.timeline()).hasSize(21);  // 0~20분
    }

    @Test
    void CS격차가_가장_많이_줄어든_3분_구간을_찾는다() {
        LaneReport report = LaneAnalyzer.analyze(sampleMatch("TOP"), sampleTimeline(), ME);

        assertThat(report.biggestCsSwing()).isNotNull();
        // 12분 이후 매 분 격차가 6씩 줄어서, 3분이면 -18. 가장 먼저 나온 12→15분 구간을 고른다
        assertThat(report.biggestCsSwing().fromMinute()).isEqualTo(12);
        assertThat(report.biggestCsSwing().toMinute()).isEqualTo(15);
        assertThat(report.biggestCsSwing().change()).isEqualTo(-18);
    }

    @Test
    void 데스_상황을_정리한다() {
        LaneReport report = LaneAnalyzer.analyze(sampleMatch("TOP"), sampleTimeline(), ME);

        assertThat(report.deaths()).hasSize(1);
        LaneReport.Death death = report.deaths().get(0);
        assertThat(death.time()).isEqualTo("13:20");
        assertThat(death.killer()).isEqualTo("Vi");
        assertThat(death.assisters()).containsExactly("Kennen");
        assertThat(death.ganked()).isTrue();             // 정글이 참여
        assertThat(death.zone()).isEqualTo("상대 진영");
        assertThat(death.myWardsLast2Min()).isEqualTo(1); // 12:30 와드만 2분 안
    }

    @Test
    void 솔로킬_방패_포탑_와드를_센다() {
        LaneReport report = LaneAnalyzer.analyze(sampleMatch("TOP"), sampleTimeline(), ME);

        assertThat(report.soloKills()).isEqualTo(1);
        assertThat(report.soloDeaths()).isEqualTo(0);
        assertThat(report.platesTaken()).isEqualTo(2);
        assertThat(report.platesLost()).isEqualTo(1);
        assertThat(report.myOuterTowerLostAt()).isEqualTo("14:00");
        assertThat(report.enemyOuterTowerDestroyedAt()).isNull();
        assertThat(report.wardsPlacedBefore15()).isEqualTo(2);
    }

    @Test
    void 라인이_없는_모드는_분석하지_않는다() {
        assertThatThrownBy(() -> LaneAnalyzer.analyze(sampleMatch(""), sampleTimeline(), ME))
                .isInstanceOf(LaneAnalysisException.class);
    }

    @Test
    void 진영_구분() {
        assertThat(LaneAnalyzer.zoneOf(new Position(2000, 2000), 100)).isEqualTo("우리 진영");
        assertThat(LaneAnalyzer.zoneOf(new Position(2000, 2000), 200)).isEqualTo("상대 진영");
        assertThat(LaneAnalyzer.zoneOf(new Position(7400, 7400), 100)).isEqualTo("강가");
    }

    // ------------------------------------------------------------ 가짜 경기 데이터

    /** 나(1, 블루 탑 다리우스) / 상대 탑(6, 케넨) / 상대 정글(7, 바이) */
    static MatchDto sampleMatch(String position) {
        List<Participant> participants = List.of(
                new Participant(1, ME, "Darius", position, 100, false, 3, 6, 2, 180, 4, 14),
                new Participant(2, "ally-jg", "LeeSin", "JUNGLE", 100, false, 2, 5, 7, 30, 140, 13),
                new Participant(6, "enemy-top", "Kennen", position, 200, true, 5, 3, 4, 200, 0, 15),
                new Participant(7, "enemy-jg", "Vi", "JUNGLE", 200, true, 6, 2, 8, 25, 150, 15)
        );
        return new MatchDto(
                new MatchDto.Metadata("KR_TEST", List.of()),
                new MatchDto.Info(1920, 0L, 420, participants)
        );
    }

    /**
     * 0~25분 프레임.
     * 내 CS: 분당 8개, 12분부터 분당 4개 / 상대 CS: 분당 7개, 12분부터 분당 10개
     */
    static TimelineDto sampleTimeline() {
        List<Frame> frames = new ArrayList<>();
        for (int m = 0; m <= 25; m++) {
            int myCs = m <= 12 ? 8 * m : 96 + 4 * (m - 12);
            int oppCs = m <= 12 ? 7 * m : 84 + 10 * (m - 12);
            Map<String, ParticipantFrame> pf = new HashMap<>();
            pf.put("1", new ParticipantFrame(1, new Position(1500, 12000), 500 + myCs * 20, 1 + m / 2, 0, myCs, 0));
            pf.put("6", new ParticipantFrame(6, new Position(2500, 13000), 500 + oppCs * 20, 1 + m / 2, 0, oppCs, 0));
            pf.put("2", new ParticipantFrame(2, new Position(5000, 5000), 500, 1, 0, 0, 0));
            pf.put("7", new ParticipantFrame(7, new Position(9000, 9000), 500, 1, 0, 0, 0));

            List<Event> events = new ArrayList<>();
            long t = m * 60_000L;
            if (m == 6) events.add(kill(t, 1, 6, List.of(), new Position(1000, 11000)));          // 솔로킬
            if (m == 9) events.add(ward(t, 1));                                                 // 2분 밖
            if (m == 13) events.add(ward(12 * 60_000L + 30_000, 1));                            // 12:30
            if (m == 14) events.add(kill(13 * 60_000L + 20_000, 7, 1, List.of(6), new Position(4000, 12500)));
            if (m == 10) events.add(plate(t, 200));
            if (m == 11) events.add(plate(t, 200));
            if (m == 13) events.add(plate(t, 100));
            if (m == 14) events.add(tower(t, 100));
            frames.add(new Frame(t, pf, events));
        }
        return new TimelineDto(new TimelineDto.Info(60_000, frames, List.of()));
    }

    static Event kill(long t, int killer, int victim, List<Integer> assists, Position pos) {
        return new Event("CHAMPION_KILL", t, null, killer, victim, assists, pos, null, null, null, null, null, null);
    }

    static Event ward(long t, int creator) {
        return new Event("WARD_PLACED", t, null, null, null, null, null, creator, "YELLOW_TRINKET", null, null, null, null);
    }

    static Event plate(long t, int ownerTeam) {
        return new Event("TURRET_PLATE_DESTROYED", t, null, 1, null, null, null, null, null, ownerTeam, null, "TOP_LANE", null);
    }

    static Event tower(long t, int ownerTeam) {
        return new Event("BUILDING_KILL", t, null, 6, null, null, null, null, null, ownerTeam,
                "TOWER_BUILDING", "TOP_LANE", "OUTER_TURRET");
    }
}
