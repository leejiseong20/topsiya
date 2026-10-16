package com.topsiya.coach;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.topsiya.lane.LaneReport;
import com.topsiya.lane.LaneService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoachServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String AI_JSON = """
            {"summary":"10분까지 앞섰지만 12분 이후 갱으로 역전당했다.",
             "mistakes":[{"time":"13:20","what":"상대 진영에서 갱을 당했다",
                          "why":"직전 2분 와드 0개, 바이가 합류","fix":"라인을 밀기 전에 강가 와드를 먼저 박는다"}],
             "goodPoints":["6분 솔로킬"],
             "nextGameTip":"라인을 밀었으면 정글 위치부터 확인하자."}
            """;

    @Test
    void Responses_API_응답에서_결과_글만_꺼낸다() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"id":"resp_1","status":"completed","output":[
                  {"type":"reasoning","summary":[]},
                  {"type":"message","role":"assistant","content":[
                    {"type":"output_text","text":"{\\"summary\\":\\"ok\\"}"}
                  ]}
                ]}
                """);

        assertThat(OpenAiClient.extractOutputText(response)).isEqualTo("{\"summary\":\"ok\"}");
    }

    @Test
    void AI가_거부하면_오류를_낸다() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"output":[{"type":"message","content":[{"type":"refusal","refusal":"no"}]}]}
                """);

        assertThatThrownBy(() -> OpenAiClient.extractOutputText(response))
                .isInstanceOf(CoachingException.class);
    }

    @Test
    void 라인전_요약을_AI에_보내고_리포트를_만든다() {
        LaneService laneService = mock(LaneService.class);
        OpenAiClient openAiClient = mock(OpenAiClient.class);
        when(laneService.analyze("KR_1", "p")).thenReturn(sampleLaneReport());
        when(openAiClient.createJsonResponse(anyString(), anyString(), eq("coaching_report"), anyMap()))
                .thenReturn(AI_JSON);

        CoachService service = new CoachService(laneService, openAiClient, objectMapper);
        CoachingReport report = service.coach("KR_1", "p");

        assertThat(report.mistakes()).hasSize(1);
        assertThat(report.mistakes().get(0).time()).isEqualTo("13:20");
        assertThat(report.goodPoints()).containsExactly("6분 솔로킬");
        // AI 에는 라인전 요약 JSON 이 들어가야 한다
        verify(openAiClient).createJsonResponse(anyString(), contains("\"myChampion\":\"Darius\""),
                eq("coaching_report"), anyMap());
    }

    @Test
    void 같은_경기는_AI를_다시_부르지_않는다() {
        LaneService laneService = mock(LaneService.class);
        OpenAiClient openAiClient = mock(OpenAiClient.class);
        when(laneService.analyze("KR_1", "p")).thenReturn(sampleLaneReport());
        when(openAiClient.createJsonResponse(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(AI_JSON);

        CoachService service = new CoachService(laneService, openAiClient, objectMapper);
        service.coach("KR_1", "p");
        service.coach("KR_1", "p");

        verify(openAiClient, times(1)).createJsonResponse(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void 깨진_AI_응답은_오류() {
        CoachService service = new CoachService(mock(LaneService.class), mock(OpenAiClient.class), objectMapper);

        assertThatThrownBy(() -> service.parse("not json"))
                .isInstanceOf(CoachingException.class);
    }

    private static LaneReport sampleLaneReport() {
        return new LaneReport(
                "KR_1", "TOP", false, 1920, "Darius", "Kennen",
                List.of(new LaneReport.Checkpoint(10, 80, 70, 10, 200, 0)),
                List.of(new LaneReport.MinutePoint(10, 80, 70, 10, 200)),
                new LaneReport.Swing(12, 15, -18),
                List.of(new LaneReport.Death("13:20", 800, "Vi", List.of("Kennen"), "정글 갱", true, false, "상대 진영", 0)),
                1, 0, 2, 1, "14:00", null, 2
        );
    }
}
