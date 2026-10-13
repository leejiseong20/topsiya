package com.topsiya.match;

/**
 * 경기 목록 화면에 보여줄 한 경기 요약 (내 기준)
 *
 * @param opponentChampionName 같은 라인 상대 챔피언. 라인이 없는 모드(칼바람 등)는 null
 */
public record MatchSummary(
        String matchId,
        int queueId,
        String championName,
        String position,
        boolean win,
        int kills,
        int deaths,
        int assists,
        int cs,
        int champLevel,
        long gameDurationSeconds,
        long gameEndTimestamp,
        String opponentChampionName
) {
}
