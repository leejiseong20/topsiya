package com.topsiya.coach;

import org.springframework.http.HttpStatus;

/** AI 코칭 중 생긴 오류. 사용자에게 보여줄 메시지와 응답 상태 코드를 같이 담는다 */
public class CoachingException extends RuntimeException {

    private final HttpStatus status;

    public CoachingException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
