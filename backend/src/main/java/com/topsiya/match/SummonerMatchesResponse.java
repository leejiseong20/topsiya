package com.topsiya.match;

import java.util.List;

public record SummonerMatchesResponse(
        String gameName,
        String tagLine,
        List<MatchSummary> matches
) {
}
