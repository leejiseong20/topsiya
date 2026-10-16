package com.topsiya.weakness;

import java.util.List;

/**
 * 여러 경기의 라인전 요약을 서버가 직접 집계한 통계.
 * AI 는 이 숫자를 "해석"만 하고, 숫자 자체는 계산하지 않는다 (계산 실수 방지).
 *
 * diff 값은 모두 "나 - 상대" 평균이다.
 */
public record WeaknessStats(
        String position,
        int games,
        int wins,

        /* 평균 격차 (해당 시점까지 간 경기만 평균) */
        Double avgCsDiffAt10,
        Double avgCsDiffAt15,
        Double avgGoldDiffAt15,
        int gamesBehindInCsAt15,

        /* 15분까지 데스 */
        double avgDeathsBefore15,
        int deathsBefore15,
        int gankedDeathsBefore15,
        int deathsWithNoWardBefore15,
        int deathsInEnemyZoneBefore15,
        int soloDeaths,
        int soloKills,

        /* 시야·포탑 */
        double avgWardsBefore15,
        int gamesLostOuterTowerFirst,
        double avgPlatesTaken,
        double avgPlatesLost,

        /* 가장 크게 손해 본 3분 구간이 시작된 시점 분포 */
        SwingBuckets swingStarts,

        /* 경기별 한 줄 요약 */
        List<GameDigest> perGame
) {

    /** biggestCsSwing.fromMinute 가 어느 구간에 몰려 있는지 (예: 10~15분에 자주 무너짐) */
    public record SwingBuckets(int from0to5, int from5to10, int from10to15, int from15to20) {
    }

    public record GameDigest(
            String matchId,
            String myChampion,
            String opponentChampion,
            boolean win,
            Integer csDiffAt10,
            Integer csDiffAt15,
            int deathsBefore15,
            int gankedDeathsBefore15,
            int deathsWithNoWardBefore15,
            String biggestSwing
    ) {
    }
}
