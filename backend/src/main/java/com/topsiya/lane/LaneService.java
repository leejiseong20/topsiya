package com.topsiya.lane;

import com.topsiya.riot.RiotApiClient;
import com.topsiya.riot.dto.MatchDto;
import com.topsiya.riot.dto.TimelineDto;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LaneService {

    private final RiotApiClient riotApiClient;

    /**
     * 끝난 경기 데이터는 바뀌지 않으니, 한 번 만든 라인전 요약은 저장해 두고 재사용한다.
     * (약점 분석에서 같은 경기를 다시 쓸 때 Riot API 를 또 부르지 않기 위해)
     * 서버를 재시작하면 비워진다.
     */
    private final Map<String, LaneReport> cache = new ConcurrentHashMap<>();

    public LaneService(RiotApiClient riotApiClient) {
        this.riotApiClient = riotApiClient;
    }

    /** Riot API 호출 2번(경기 상세 + 타임라인) 후 라인전 요약을 만든다 */
    public LaneReport analyze(String matchId, String puuid) {
        String key = matchId + ":" + puuid;
        LaneReport cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        MatchDto match = riotApiClient.getMatch(matchId);
        TimelineDto timeline = riotApiClient.getTimeline(matchId);
        LaneReport report = LaneAnalyzer.analyze(match, timeline, puuid);
        cache.put(key, report);
        return report;
    }
}
