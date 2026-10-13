package com.topsiya.match;

import java.util.List;

/**
 * @param puuid 라인전 분석(/api/matches/{matchId}/lane?puuid=...) 요청에 그대로 쓴다
 */
public record SummonerMatchesResponse(
        String gameName,
        String tagLine,
        String puuid,
        List<MatchSummary> matches
) {
}
