package com.topsiya.coach;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.topsiya.lane.LaneReport;
import com.topsiya.lane.LaneService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CoachService {

    private final LaneService laneService;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    /**
     * 같은 경기를 다시 누르면 AI 를 또 부르지 않고 저장해 둔 결과를 쓴다 (비용 절약).
     * 서버를 재시작하면 비워진다. 나중에 DB 저장으로 바꿀 예정.
     */
    private final Map<String, CoachingReport> cache = new ConcurrentHashMap<>();

    public CoachService(LaneService laneService, OpenAiClient openAiClient, ObjectMapper objectMapper) {
        this.laneService = laneService;
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
    }

    public CoachingReport coach(String matchId, String puuid) {
        String key = matchId + ":" + puuid;
        CoachingReport cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        // 1) Riot 데이터 → 라인전 요약 (원본 타임라인 대신 이것만 AI 에 보낸다)
        LaneReport laneReport = laneService.analyze(matchId, puuid);

        // 2) 요약 → AI → 코칭 리포트
        String outputJson = openAiClient.createJsonResponse(
                CoachPrompt.INSTRUCTIONS,
                toJson(laneReport),
                "coaching_report",
                CoachPrompt.RESPONSE_SCHEMA
        );
        CoachingReport report = parse(outputJson);

        cache.put(key, report);
        return report;
    }

    String toJson(LaneReport laneReport) {
        try {
            return objectMapper.writeValueAsString(laneReport);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("라인전 요약을 JSON 으로 바꾸지 못했어요.", e);
        }
    }

    CoachingReport parse(String outputJson) {
        try {
            return objectMapper.readValue(outputJson, CoachingReport.class);
        } catch (JsonProcessingException e) {
            throw new CoachingException(HttpStatus.BAD_GATEWAY,
                    "AI 응답을 읽지 못했어요. 다시 시도하세요.");
        }
    }
}
