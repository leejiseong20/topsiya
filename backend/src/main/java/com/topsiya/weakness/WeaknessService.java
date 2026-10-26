package com.topsiya.weakness;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.topsiya.coach.CoachingException;
import com.topsiya.coach.OpenAiClient;
import com.topsiya.lane.LaneAnalysisException;
import com.topsiya.lane.LaneReport;
import com.topsiya.lane.LaneService;
import com.topsiya.store.FileStore;
import com.topsiya.verify.CheckedAnswer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

@Service
public class WeaknessService {

    static final int MIN_GAMES = 2;
    static final int MAX_GAMES = 10;

    private final LaneService laneService;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    /** 같은 경기 묶음을 다시 분석하면 AI 를 또 부르지 않는다 (파일 저장, 재시작해도 유지) */
    private final FileStore store;

    public static final String CATEGORY = "weakness";

    public WeaknessService(LaneService laneService, OpenAiClient openAiClient, ObjectMapper objectMapper,
                           FileStore store) {
        this.laneService = laneService;
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
        this.store = store;
    }

    public WeaknessReport analyze(WeaknessRequest request) {
        List<String> ids = request.matchIds() == null ? List.of() : request.matchIds();
        if (ids.size() < MIN_GAMES) {
            throw new LaneAnalysisException("약점 분석은 같은 포지션 경기가 " + MIN_GAMES + "판 이상 있어야 해요.");
        }
        if (ids.size() > MAX_GAMES) {
            ids = ids.subList(0, MAX_GAMES);
        }

        String key = request.puuid() + ":" + request.position() + ":" + new TreeSet<>(ids);
        var saved = store.read(CATEGORY, key, WeaknessReport.class);
        if (saved.isPresent()) {
            return saved.get();
        }

        // 1) 경기마다 라인전 요약 (이미 분석한 경기는 LaneService 가 저장해 둔 것을 쓴다)
        List<LaneReport> reports = new ArrayList<>();
        for (String matchId : ids) {
            try {
                reports.add(laneService.analyze(matchId, request.puuid()));
            } catch (LaneAnalysisException e) {
                // 맞라인이 없는 경기 등은 건너뛴다
            }
        }
        if (reports.size() < MIN_GAMES) {
            throw new LaneAnalysisException("분석할 수 있는 경기가 " + MIN_GAMES + "판보다 적어요.");
        }

        // 2) 통계는 서버가 계산
        WeaknessStats stats = WeaknessAggregator.aggregate(request.position(), reports);

        // 3) 해석만 AI 에게. AI 가 쓴 숫자가 통계와 안 맞으면 한 번 다시 쓰게 한다
        CheckedAnswer<WeaknessReport.AiPart> checked = CheckedAnswer.ask(
                toJson(stats),
                input -> parse(openAiClient.createJsonResponse(
                        WeaknessPrompt.INSTRUCTIONS, input, "weakness_report", WeaknessPrompt.RESPONSE_SCHEMA)),
                draft -> WeaknessEvidence.verify(stats, draft, objectMapper)
        );
        WeaknessReport report = new WeaknessReport(stats, checked.answer(), checked.verification(), checked.firstDraft());

        store.write(CATEGORY, key, report);
        return report;
    }

    private String toJson(WeaknessStats stats) {
        try {
            return objectMapper.writeValueAsString(stats);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("통계를 JSON 으로 바꾸지 못했어요.", e);
        }
    }

    WeaknessReport.AiPart parse(String outputJson) {
        try {
            return objectMapper.readValue(outputJson, WeaknessReport.AiPart.class);
        } catch (JsonProcessingException e) {
            throw new CoachingException(HttpStatus.BAD_GATEWAY, "AI 응답을 읽지 못했어요. 다시 시도하세요.");
        }
    }
}
