package com.topsiya.riot;

public class RiotApiKeyMissingException extends RuntimeException {

    public RiotApiKeyMissingException() {
        super("Riot API 키가 설정되지 않았어요. application-secret.yml 또는 RIOT_API_KEY 환경변수를 확인하세요.");
    }
}
