package com.topsiya.coach;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.topsiya.lane.LaneReport;
import com.topsiya.lane.LaneService;
import com.topsiya.store.FileStore;
import com.topsiya.verify.CheckedAnswer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CoachService {

    private final LaneService laneService;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    /**
     * 같은 경기를 다시 누르면 AI 를 또 부르지 않고 파일에 저장해 둔 결과를 쓴다 (비용 절약).
     * 서버를 재시작해도 남아 있다.
     */
    private final FileStore store;

    public static final String CATEGORY = "coach";

    public CoachService(LaneService laneService, OpenAiClient openAiClient, ObjectMapper objectMapper,
                        FileStore store) {
        this.laneService = laneService;
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
        this.store = store;
    }

    public CoachingReport coach(String matchId, String puuid) {
        String key = matchId + "_" + puuid;
        var saved = store.read(CATEGORY, key, CoachingReport.class);
        if (saved.isPresent()) {
            return saved.get();
        }

        // 1) Riot 데이터 → 라인전 요약 (원본 타임라인 대신 이것만 AI 에 보낸다)
        LaneReport laneReport = laneService.analyze(matchId, puuid);

        // 2) 요약 → AI → 코칭 리포트
        // 3) AI 가 쓴 시각·숫자를 경기 데이터와 대조 → 안 맞으면 틀린 곳을 알려주고 한 번 다시 쓰게 한다
        CheckedAnswer<CoachingReport> checked = CheckedAnswer.ask(
                toJson(laneReport),
                input -> parse(openAiClient.createJsonResponse(
                        CoachPrompt.INSTRUCTIONS, input, "coaching_report", CoachPrompt.RESPONSE_SCHEMA)),
                draft -> CoachEvidence.verify(laneReport, draft, objectMapper)
        );
        CoachingReport report = checked.answer().withChecks(checked.verification(), checked.firstDraft());

        store.write(CATEGORY, key, report);
        return report;
    }

    /** 저장된 코칭을 지금 검사 규칙으로 다시 검증한다 (정확도 측정용, AI 호출 없음) */
    public com.topsiya.verify.Verification recheck(String matchId, String puuid, CoachingReport report) {
        return CoachEvidence.verify(laneService.analyze(matchId, puuid), report, objectMapper);
    }

    /** 검사기 시험: 저장된 코칭에 일부러 틀린 내용을 심어 잡아내는지 본다 (AI 호출 없음) */
    public java.util.List<CoachProbe.Result> probe(String matchId, String puuid, CoachingReport report) {
        return CoachProbe.run(laneService.analyze(matchId, puuid), report, objectMapper);
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
