package com.topsiya.weakness;

import com.topsiya.lane.LaneReport;
import com.topsiya.lane.LaneReport.Checkpoint;
import com.topsiya.lane.LaneReport.Death;
import com.topsiya.lane.LaneReport.Swing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeaknessAggregatorTest {

    @Test
    void 여러_경기를_집계한다() {
        WeaknessStats s = WeaknessAggregator.aggregate("TOP", List.of(gameA(), gameB(), gameC()));

        assertThat(s.games()).isEqualTo(3);
        assertThat(s.wins()).isEqualTo(1);

        // 10분 CS 격차: +10, -20, (C는 10분 전에 끝남) → 평균 -5.0
        assertThat(s.avgCsDiffAt10()).isEqualTo(-5.0);
        // 15분: -6, -30 → 평균 -18.0, 둘 다 뒤처짐
        assertThat(s.avgCsDiffAt15()).isEqualTo(-18.0);
        assertThat(s.gamesBehindInCsAt15()).isEqualTo(2);

        // 15분(900초)까지 데스만: A 1번, B 2번, C 0번 (C의 데스는 16분)
        assertThat(s.deathsBefore15()).isEqualTo(3);
        assertThat(s.avgDeathsBefore15()).isEqualTo(1.0);
        assertThat(s.gankedDeathsBefore15()).isEqualTo(2);
        assertThat(s.deathsWithNoWardBefore15()).isEqualTo(2);
        assertThat(s.deathsInEnemyZoneBefore15()).isEqualTo(2);

        assertThat(s.soloKills()).isEqualTo(1);
        assertThat(s.soloDeaths()).isEqualTo(1);
        assertThat(s.avgWardsBefore15()).isEqualTo(3.0);   // (2 + 4 + 3) / 3
        assertThat(s.gamesLostOuterTowerFirst()).isEqualTo(2); // A: 상대 안 깨짐, B: 내가 먼저
        assertThat(s.avgPlatesTaken()).isEqualTo(1.0);
        assertThat(s.avgPlatesLost()).isEqualTo(2.3);

        // 손해 구간 시작: 12분, 13분 → 10~15분대 2번
        assertThat(s.swingStarts().from10to15()).isEqualTo(2);
        assertThat(s.swingStarts().from0to5()).isZero();

        assertThat(s.perGame()).hasSize(3);
        assertThat(s.perGame().get(0).biggestSwing()).isEqualTo("12~15분 -18");
        assertThat(s.perGame().get(2).csDiffAt10()).isNull();
    }

    @Test
    void 포탑_먼저_잃었는지_판단() {
        assertThat(WeaknessAggregator.lostOuterTowerFirst(gameA())).isTrue();   // 14:00, 상대 안 깨짐
        assertThat(WeaknessAggregator.lostOuterTowerFirst(gameB())).isTrue();   // 12:00 < 18:30
        assertThat(WeaknessAggregator.lostOuterTowerFirst(gameC())).isFalse();  // 내 포탑 안 깨짐
        assertThat(WeaknessAggregator.toSeconds("15:59")).isEqualTo(959);
    }

    // -------------------------------------------------------------- 가짜 경기

    /** 패배. 10분 +10 → 15분 -6, 13:20 갱 데스(상대 진영, 와드 0) */
    static LaneReport gameA() {
        return report("A", false,
                List.of(cp(10, 10, 200), cp(15, -6, -120)),
                new Swing(12, 15, -18),
                List.of(death(800, true, "상대 진영", 0)),
                1, 0, 2, 2, 1, "14:00", null);
    }

    /** 패배. 10분 -20 → 15분 -30, 데스 2번(갱 1 + 솔로 1) */
    static LaneReport gameB() {
        return report("B", false,
                List.of(cp(10, -20, -500), cp(15, -30, -900)),
                new Swing(13, 16, -12),
                List.of(death(400, false, "우리 진영", 1), death(700, true, "상대 진영", 0)),
                0, 1, 4, 1, 4, "12:00", "18:30");
    }

    /** 승리. 9분에 끝난 경기라 체크포인트 없음, 데스는 16분 */
    static LaneReport gameC() {
        return report("C", true,
                List.of(cp(5, 3, 50)),
                null,
                List.of(death(960, false, "강가", 2)),
                0, 0, 3, 0, 2, null, "8:30");
    }

    static LaneReport report(String id, boolean win, List<Checkpoint> cps, Swing swing, List<Death> deaths,
                             int soloKills, int soloDeaths, int wards, int platesTaken, int platesLost,
                             String myTower, String enemyTower) {
        return new LaneReport(id, "TOP", win, 1800, "Darius", "Kennen", cps, List.of(), swing, deaths,
                soloKills, soloDeaths, platesTaken, platesLost, myTower, enemyTower, wards);
    }

    static Checkpoint cp(int minute, int csDiff, int goldDiff) {
        return new Checkpoint(minute, 0, 0, csDiff, goldDiff, 0);
    }

    static Death death(long seconds, boolean ganked, String zone, int wards) {
        return new Death(seconds / 60 + ":" + String.format("%02d", seconds % 60), seconds, "Kennen",
                List.of(), ganked, zone, wards);
    }
}
