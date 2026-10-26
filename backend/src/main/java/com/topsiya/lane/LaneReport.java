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
        int wardsPlacedBefore15,

        /* 내 팀: 100 = 블루(왼쪽 아래 기지), 200 = 레드(오른쪽 위 기지). 미니맵 진영 표시용, 모르면 0 */
        int myTeamId,

        /* 라인전이 끝난 시각(초) = 내 라인 1차 포탑(우리 것이든 상대 것이든)이 처음 깨진 때. 안 깨졌으면 null */
        Long laneEndSeconds,
        /* "상대 1차 포탑 파괴" / "우리 1차 포탑 파괴" */
        String laneEndReason
) {

    /** 블루 진영 경계: 좌표 x + y 가 이보다 작으면 블루 쪽 (LaneAnalyzer.zoneOf 와 미니맵이 같은 값을 쓴다) */
    public static final int BLUE_SIDE_MAX = 13_800;
    /** 레드 진영 경계: x + y 가 이보다 크면 레드 쪽. 그 사이는 강가 */
    public static final int RED_SIDE_MIN = 15_800;

    /** 팀 정보 없이 만들 때 (테스트 등) */
    public LaneReport(String matchId, String position, boolean win, long gameDurationSeconds, String myChampion,
                      String opponentChampion, List<Checkpoint> checkpoints, List<MinutePoint> timeline,
                      Swing biggestCsSwing, List<Death> deaths, int soloKills, int soloDeaths, int platesTaken,
                      int platesLost, String myOuterTowerLostAt, String enemyOuterTowerDestroyedAt,
                      int wardsPlacedBefore15) {
        this(matchId, position, win, gameDurationSeconds, myChampion, opponentChampion, checkpoints, timeline,
                biggestCsSwing, deaths, soloKills, soloDeaths, platesTaken, platesLost, myOuterTowerLostAt,
                enemyOuterTowerDestroyedAt, wardsPlacedBefore15, 0, null, null);
    }

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
     * @param x               죽은 위치 (맵 좌표 0~15000, 왼쪽 아래가 0). 미니맵 표시용, 없으면 null
     * @param y               죽은 위치 (위로 갈수록 큼)
     * @param lanePhase       라인전 중 데스인지 (내 라인 1차 포탑이 처음 깨지기 전). 라인전 이후엔 갱·로밍으로 부르지 않는다
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
            int myWardsLast2Min,
            Integer x,
            Integer y,
            boolean lanePhase
    ) {

        /** 위치 없이, 라인전 중 데스로 만들 때 (테스트 등) */
        public Death(String time, long seconds, String killer, List<String> assisters, String type,
                     boolean jungleInvolved, boolean roamInvolved, String zone, int myWardsLast2Min) {
            this(time, seconds, killer, assisters, type, jungleInvolved, roamInvolved, zone, myWardsLast2Min,
                    null, null, true);
        }

        /** 라인전 중 정글이 와서 죽음 (반복 실수·목표의 "정글 갱" 기준) */
        public boolean laneGank() {
            return lanePhase && jungleInvolved;
        }

        /** 라인전 중 다른 라인 라이너가 와서 죽음 (라인전 이후는 로밍이 아니다) */
        public boolean laneRoam() {
            return lanePhase && roamInvolved;
        }
    }
}
