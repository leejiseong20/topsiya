package com.topsiya.coach;

import com.fasterxml.jackson.databind.JsonNode;
import com.topsiya.store.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * OpenAI Responses API 호출만 담당한다.
 * 문서: https://platform.openai.com/docs/api-reference/responses
 */
@Component
public class OpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

    private final RestClient openAiRestClient;
    private final OpenAiProperties properties;
    private final AppProperties app;

    public OpenAiClient(@Qualifier("openAiRestClient") RestClient openAiRestClient,
                        OpenAiProperties properties, AppProperties app) {
        this.openAiRestClient = openAiRestClient;
        this.properties = properties;
        this.app = app;
    }

    /**
     * 지시문 + 입력을 보내고, schema 구조로 강제된 JSON 문자열을 돌려받는다.
     */
    public String createJsonResponse(String instructions, String input,
                                     String schemaName, Map<String, Object> schema) {
        if (app.demoMode()) {
            throw new CoachingException(HttpStatus.SERVICE_UNAVAILABLE,
                    "데모 모드에서는 미리 분석해 둔 경기만 AI 결과를 볼 수 있어요.");
        }
        if (!properties.hasApiKey()) {
            throw new CoachingException(HttpStatus.SERVICE_UNAVAILABLE,
                    "OpenAI API 키가 설정되지 않았어요. application-secret.yml 을 확인하세요.");
        }

        Map<String, Object> body = Map.of(
                "model", properties.model(),
                "instructions", instructions,
                "input", input,
                "text", Map.of("format", Map.of(
                        "type", "json_schema",
                        "name", schemaName,
                        "strict", true,
                        "schema", schema
                ))
        );

        JsonNode response;
        try {
            response = openAiRestClient.post()
                    .uri("/v1/responses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            log.warn("OpenAI 연결 실패", e);
            throw new CoachingException(HttpStatus.BAD_GATEWAY,
                    "OpenAI 서버에 연결할 수 없거나 응답이 너무 오래 걸렸어요. 잠시 후 다시 시도하세요.");
        }
        return extractOutputText(response);
    }

    /**
     * Responses API 응답에서 AI 가 쓴 글만 꺼낸다.
     * output[] 중 type=message 항목의 content[] 에서 type=output_text 의 text.
     */
    static String extractOutputText(JsonNode response) {
        if (response == null) {
            throw new CoachingException(HttpStatus.BAD_GATEWAY, "OpenAI 응답이 비어 있어요.");
        }
        for (JsonNode item : response.path("output")) {
            if (!"message".equals(item.path("type").asText())) continue;
            for (JsonNode content : item.path("content")) {
                String type = content.path("type").asText();
                if ("output_text".equals(type)) {
                    return content.path("text").asText();
                }
                if ("refusal".equals(type)) {
                    throw new CoachingException(HttpStatus.BAD_GATEWAY,
                            "AI 가 응답을 거부했어요: " + content.path("refusal").asText());
                }
            }
        }
        String status = response.path("status").asText();
        throw new CoachingException(HttpStatus.BAD_GATEWAY,
                "AI 응답에서 결과를 찾지 못했어요. (status: " + status + ")");
    }

    private CoachingException translate(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String detail = e.getResponseBodyAsString();
        log.warn("OpenAI API 오류 {}: {}", status, detail);

        return switch (status) {
            case 401 -> new CoachingException(HttpStatus.SERVICE_UNAVAILABLE,
                    "OpenAI API 키가 잘못됐어요. application-secret.yml 의 openai.api-key 를 확인하세요.");
            case 403, 404 -> new CoachingException(HttpStatus.SERVICE_UNAVAILABLE,
                    "이 API 키로는 모델 '" + properties.model() + "' 을 쓸 수 없어요. "
                            + "application.yml 의 openai.model 을 사용 가능한 모델로 바꾸세요.");
            case 429 -> new CoachingException(HttpStatus.TOO_MANY_REQUESTS,
                    "OpenAI 사용 한도를 넘었어요. (요청이 너무 많거나 크레딧이 부족해요)");
            case 400 -> new CoachingException(HttpStatus.BAD_GATEWAY,
                    "OpenAI 가 요청을 거절했어요. 서버 로그의 'OpenAI API 오류 400' 내용을 확인하세요.");
            default -> new CoachingException(HttpStatus.BAD_GATEWAY,
                    "OpenAI 서버 오류예요. (" + status + ")");
        };
    }
}
