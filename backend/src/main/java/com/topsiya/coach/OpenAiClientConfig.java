package com.topsiya.coach;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenAiClientConfig {

    /** Authorization 헤더를 자동으로 붙이는 OpenAI 전용 HTTP 클라이언트 */
    @Bean
    public RestClient openAiRestClient(OpenAiProperties properties) {
        // AI 응답은 수십 초 걸릴 수 있어서 읽기 제한 시간을 넉넉하게 둔다
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(90_000);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory);

        if (properties.hasApiKey()) {
            builder.defaultHeader("Authorization", "Bearer " + properties.apiKey());
        }
        return builder.build();
    }
}
