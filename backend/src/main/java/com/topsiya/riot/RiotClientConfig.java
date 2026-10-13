package com.topsiya.riot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RiotClientConfig {

    /** 모든 요청에 X-Riot-Token 헤더를 자동으로 붙이는 Riot 전용 HTTP 클라이언트 */
    @Bean
    public RestClient riotRestClient(RiotProperties properties) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://" + properties.regionalHost());

        if (properties.hasApiKey()) {
            builder.defaultHeader("X-Riot-Token", properties.apiKey());
        }
        return builder.build();
    }
}
