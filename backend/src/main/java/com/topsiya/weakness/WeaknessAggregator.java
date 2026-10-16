package com.topsiya.weakness;

import com.topsiya.lane.LaneReport;
import com.topsiya.lane.LaneReport.Checkpoint;
import com.topsiya.lane.LaneReport.Death;
import com.topsiya.weakness.WeaknessStats.GameDigest;
import com.topsiya.weakness.WeaknessStats.SwingBuckets;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 라인전 요약 여러 개 → 통계. 외부 호출이 없는 순수 계산 클래스 */
public final class WeaknessAggregator {

    static final long LANE_PHASE_END_SECONDS = 15 * 60;

    private WeaknessAggregator() {
    }

    public static WeaknessStats aggregate(String position, List<LaneReport> reports) {
        int games = reports.size();
        int wins = (int) reports.stream().filter(LaneReport::win).count();

        List<Integer> cs10 = new ArrayList<>();
        List<Integer> cs15 = new ArrayList<>();
        List<Integer> gold15 = new ArrayList<>();

        int deathsBefore15 = 0;
        int jungleGank = 0;
        int roam = 0;
        int noWard = 0;
        int enemyZone = 0;
        int soloDeaths = 0;
        int soloKills = 0;
        int wards = 0;
        int lostTowerFirst = 0;
        int platesTaken = 0;
        int platesLost = 0;
        int[] swing = new int[4];
        List<GameDigest> digests = new ArrayList<>();

        for (LaneReport r : reports) {
            Integer at10 = checkpoint(r, 10).map(Checkpoint::csDiff).orElse(null);
            Integer at15 = checkpoint(r, 15).map(Checkpoint::csDiff).orElse(null);
            if (at10 != null) cs10.add(at10);
            if (at15 != null) cs15.add(at15);
            checkpoint(r, 15).ifPresent(c -> gold15.add(c.goldDiff()));

            List<Death> early = r.deaths().stream()
                    .filter(d -> d.seconds() <= LANE_PHASE_END_SECONDS)
                    .toList();
            int gameJungle = (int) early.stream().filter(Death::jungleInvolved).count();
            int gameRoam = (int) early.stream().filter(Death::roamInvolved).count();
            int gameNoWard = (int) early.stream().filter(d -> d.myWardsLast2Min() == 0).count();

            deathsBefore15 += early.size();
            jungleGank += gameJungle;
            roam += gameRoam;
            noWard += gameNoWard;
            enemyZone += (int) early.stream().filter(d -> "상대 진영".equals(d.zone())).count();
            soloDeaths += r.soloDeaths();
            soloKills += r.soloKills();
            wards += r.wardsPlacedBefore15();
            platesTaken += r.platesTaken();
            platesLost += r.platesLost();
            if (lostOuterTowerFirst(r)) lostTowerFirst++;

            if (r.biggestCsSwing() != null) {
                int bucket = Math.min(r.biggestCsSwing().fromMinute() / 5, 3);
                swing[bucket]++;
            }

            digests.add(new GameDigest(
                    r.matchId(),
                    r.myChampion(),
                    r.opponentChampion(),
                    r.win(),
                    at10,
                    at15,
                    early.size(),
                    gameJungle,
                    gameRoam,
                    gameNoWard,
                    r.biggestCsSwing() == null ? null
                            : r.biggestCsSwing().fromMinute() + "~" + r.biggestCsSwing().toMinute()
                              + "분 " + r.biggestCsSwing().change()
            ));
        }

        int behindAt15 = (int) cs15.stream().filter(d -> d < 0).count();

        return new WeaknessStats(
                position,
                games,
                wins,
                average(cs10),
                average(cs15),
                average(gold15),
                behindAt15,
                round1(games == 0 ? 0 : (double) deathsBefore15 / games),
                deathsBefore15,
                jungleGank,
                roam,
                noWard,
                enemyZone,
                soloDeaths,
                soloKills,
                round1(games == 0 ? 0 : (double) wards / games),
                lostTowerFirst,
                round1(games == 0 ? 0 : (double) platesTaken / games),
                round1(games == 0 ? 0 : (double) platesLost / games),
                new SwingBuckets(swing[0], swing[1], swing[2], swing[3]),
                digests
        );
    }

    /**
     * 내 1차 포탑이 상대 1차 포탑보다 먼저 깨졌는지.
     * 상대 포탑이 안 깨졌는데 내 포탑만 깨진 경우도 포함한다.
     */
    static boolean lostOuterTowerFirst(LaneReport r) {
        Integer mine = toSeconds(r.myOuterTowerLostAt());
        Integer theirs = toSeconds(r.enemyOuterTowerDestroyedAt());
        if (mine == null) return false;
        return theirs == null || mine < theirs;
    }

    private static Optional<Checkpoint> checkpoint(LaneReport r, int minute) {
        return r.checkpoints().stream().filter(c -> c.minute() == minute).findFirst();
    }

    /** "15:59" → 959 */
    static Integer toSeconds(String mmss) {
        if (mmss == null || !mmss.contains(":")) return null;
        String[] parts = mmss.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    static Double average(List<Integer> values) {
        if (values.isEmpty()) return null;
        double avg = values.stream().filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0);
        return round1(avg);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
