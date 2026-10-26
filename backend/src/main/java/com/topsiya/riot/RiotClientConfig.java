package com.topsiya.riot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RiotClientConfig {

    /** 모든 요청에 X-Riot-Token 헤더를 자동으로 붙이는 Riot 전용 HTTP 클라이언트 */
    @Bean
    public RestClient riotRestClient(RiotProperties properties) {
        // 인터넷이 불안정할 때 오래 붙잡혀 있지 않고 빨리 저장본으로 넘어가도록 제한 시간을 둔다
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3_000);
        requestFactory.setReadTimeout(10_000);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://" + properties.regionalHost())
                .requestFactory(requestFactory);

        if (properties.hasApiKey()) {
            builder.defaultHeader("X-Riot-Token", properties.apiKey());
        }
        return builder.build();
    }
}
