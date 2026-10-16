package com.topsiya.lane;

import java.util.List;

/**
 * 한 경기의 라인전 요약.
 * 3주차에 이 객체를 그대로 JSON 으로 만들어 AI 에게 넘긴다. (원본 타임라인 대신 이것만 보냄)
 *
 * 모든 diff 값은 "나 - 상대" 이다. 양수면 내가 앞섰고, 음수면 뒤처졌다.
 */
public record LaneReport(
        String matchId,
        String position,
        boolean win,
        long gameDurationSeconds,
        String myChampion,
        String opponentChampion,

        /* 5·10·15분 시점 비교 */
        List<Checkpoint> checkpoints,

        /* 0분 ~ 20분 분 단위 추이 (그래프용) */
        List<MinutePoint> timeline,

        /* 3분 동안 CS 격차가 가장 크게 벌어진(손해 본) 구간. 손해 본 구간이 없으면 null */
        Swing biggestCsSwing,

        /* 20분까지의 내 데스 */
        List<Death> deaths,

        /* 15분까지 1:1 킬/데스 (어시스트 없이) */
        int soloKills,
        int soloDeaths,

        /* 내 라인 포탑 방패 (라인이 없는 포지션은 0) */
        int platesTaken,
        int platesLost,

        /* 내 라인 1차 포탑 (mm:ss). 안 깨졌으면 null */
        String myOuterTowerLostAt,
        String enemyOuterTowerDestroyedAt,

        /* 15분까지 내가 설치한 와드 수 */
        int wardsPlacedBefore15
) {

    public record Checkpoint(int minute, int myCs, int opponentCs, int csDiff, int goldDiff, int levelDiff) {
    }

    public record MinutePoint(int minute, int myCs, int opponentCs, int csDiff, int goldDiff) {
    }

    /** change 는 음수 (예: -12 = 그 3분 동안 CS 격차가 12 줄었다) */
    public record Swing(int fromMinute, int toMinute, int change) {
    }

    /**
     * @param type            데스 유형: "라인 교전" / "정글 갱" / "상대 로밍" / "정글+로밍" / "한타" / "포탑/미니언"
     * @param jungleInvolved  상대 정글이 킬/어시스트에 참여
     * @param roamInvolved    라인 상대도 정글도 아닌 적(다른 라인 라이너)이 참여
     * @param zone            "우리 진영" / "강가" / "상대 진영" (대략적인 구분)
     * @param myWardsLast2Min 죽기 전 2분 동안 내가 설치한 와드 수
     */
    public record Death(
            String time,
            long seconds,
            String killer,
            List<String> assisters,
            String type,
            boolean jungleInvolved,
            boolean roamInvolved,
            String zone,
            int myWardsLast2Min
    ) {
    }
}
