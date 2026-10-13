package com.topsiya.lane;

import com.topsiya.riot.RiotApiClient;
import com.topsiya.riot.dto.MatchDto;
import com.topsiya.riot.dto.TimelineDto;
import org.springframework.stereotype.Service;

@Service
public class LaneService {

    private final RiotApiClient riotApiClient;

    public LaneService(RiotApiClient riotApiClient) {
        this.riotApiClient = riotApiClient;
    }

    /** Riot API 호출 2번(경기 상세 + 타임라인) 후 라인전 요약을 만든다 */
    public LaneReport analyze(String matchId, String puuid) {
        MatchDto match = riotApiClient.getMatch(matchId);
        TimelineDto timeline = riotApiClient.getTimeline(matchId);
        return LaneAnalyzer.analyze(match, timeline, puuid);
    }
}
