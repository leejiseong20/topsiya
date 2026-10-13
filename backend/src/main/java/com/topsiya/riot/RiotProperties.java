package com.topsiya.riot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 riot.* 설정값.
 *
 * @param apiKey       Riot Developer Portal 에서 발급한 키 (RGAPI-...). 개발용 키는 24시간마다 만료된다.
 * @param regionalHost 계정(ACCOUNT-V1)·경기(MATCH-V5) API 호스트. 한국 서버는 asia 를 쓴다.
 */
@ConfigurationProperties(prefix = "riot")
public record RiotProperties(String apiKey, String regionalHost) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
