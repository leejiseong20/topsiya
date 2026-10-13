package com.topsiya.coach;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 openai.* 설정값.
 *
 * @param apiKey  OpenAI API 키 (sk-...). application-secret.yml 에 둔다
 * @param baseUrl 기본값 https://api.openai.com
 * @param model   코칭에 쓸 모델 이름. 키에서 쓸 수 있는 모델로 바꿔 쓸 수 있다
 */
@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(String apiKey, String baseUrl, String model) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
