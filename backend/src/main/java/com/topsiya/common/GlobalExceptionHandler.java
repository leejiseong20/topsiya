package com.topsiya.common;

import com.topsiya.riot.RiotApiKeyMissingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/** Riot API 오류를 사용자가 이해할 수 있는 메시지로 바꿔서 돌려준다 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RiotApiKeyMissingException.class)
    public ResponseEntity<ErrorResponse> handleMissingKey(RiotApiKeyMissingException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleRiotError(RestClientResponseException e) {
        int riotStatus = e.getStatusCode().value();
        log.warn("Riot API 오류 {}: {}", riotStatus, e.getResponseBodyAsString());

        return switch (riotStatus) {
            case 404 -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("소환사를 찾을 수 없어요. 닉네임과 태그(#KR1 등)를 확인하세요."));
            case 401, 403 -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse("Riot API 키가 만료됐거나 잘못됐어요. 개발용 키는 24시간마다 재발급해야 해요."));
            case 429 -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ErrorResponse("요청이 너무 많아요. 1~2분 뒤에 다시 시도하세요."));
            default -> ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ErrorResponse("Riot API 서버 오류예요. (" + riotStatus + ")"));
        };
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponse> handleNetwork(ResourceAccessException e) {
        log.warn("Riot API 연결 실패", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("Riot API 서버에 연결할 수 없어요. 인터넷 연결을 확인하세요."));
    }
}
