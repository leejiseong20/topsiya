package com.topsiya.verify;

import java.util.function.Function;

/**
 * AI 답을 받아 검증하고, 데이터와 안 맞는 내용이 있으면 무엇이 틀렸는지 알려주고 한 번 다시 쓰게 한다.
 * 두 답 중 확인 안 된 내용이 적은 쪽을 고른다.
 *
 * @param verification 최종 답의 검증 결과
 * @param firstDraft   다시 쓰게 했을 때 첫 답의 검증 결과 (다시 쓰지 않았으면 null) → 정확도 측정에 쓴다
 */
public record CheckedAnswer<T>(T answer, Verification verification, Verification firstDraft) {

    public static <T> CheckedAnswer<T> ask(String input, Function<String, T> askAi, Function<T, Verification> check) {
        T first = askAi.apply(input);
        Verification firstCheck = check.apply(first);
        if (firstCheck.allMatched()) {
            return new CheckedAnswer<>(first, firstCheck, null);
        }

        T second;
        try {
            second = askAi.apply(input + "\n\n" + firstCheck.feedback());
        } catch (RuntimeException e) {
            return new CheckedAnswer<>(first, firstCheck, firstCheck); // 다시 쓰기 실패 → 첫 답 사용
        }
        Verification secondCheck = check.apply(second);
        return secondCheck.problems() <= firstCheck.problems()
                ? new CheckedAnswer<>(second, secondCheck, firstCheck)
                : new CheckedAnswer<>(first, firstCheck, firstCheck);
    }
}
