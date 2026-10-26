package com.topsiya.verify;

import java.util.List;

/**
 * 검사기 시험 결과: 저장된 AI 답에 틀린 내용을 일부러 심었을 때 몇 개를 잡았는지.
 *
 * @param caught      지금 검사기(엄격)가 잡은 수
 * @param caughtLoose 예전 느슨한 검사였다면 잡았을 수 (비교용)
 */
public record ProbeSummary(int reports, int planted, int caught, int caughtLoose, List<Row> byType) {

    public record Row(String type, int planted, int caught, int caughtLoose) {
    }
}
