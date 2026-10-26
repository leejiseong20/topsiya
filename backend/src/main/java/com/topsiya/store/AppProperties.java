package com.topsiya.store;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 topsiya.* 설정값.
 *
 * @param dataDir  Riot 응답·AI 결과를 저장할 폴더 (기본: 실행 위치의 local-data)
 * @param demoMode true 면 외부 API(Riot·OpenAI)를 부르지 않고 저장된 데이터만 쓴다 (발표 시연용)
 * @param devTools true 면 개발자용 화면(AI 답변 정확도 측정)을 연다. 일반 사용자에게는 꺼 둔다
 */
@ConfigurationProperties(prefix = "topsiya")
public record AppProperties(String dataDir, boolean demoMode, boolean devTools) {

    public String dataDirOrDefault() {
        return dataDir == null || dataDir.isBlank() ? "local-data" : dataDir;
    }
}
