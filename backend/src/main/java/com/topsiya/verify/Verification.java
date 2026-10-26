package com.topsiya.verify;

import java.util.List;

/**
 * AI 가 쓴 글 속 "시각"과 "숫자"를 실제 경기 데이터와 대조한 결과.
 *
 * @param timeClaims    글에 나온 시각·구간 수 (13:20, 12~15분)
 * @param timeMatched   그중 데이터에 실제 사건이 있는 것
 * @param numberClaims  글에 나온 숫자 수 (CS 14개, 와드 0개 ...)
 * @param numberMatched 그중 데이터에 그대로 있는 숫자
 * @param unmatched     확인되지 않은 것들 (화면·재작성 요청에 쓴다)
 * @param contextClaims  시각과 짝지은 내용 (그 시각 데스의 유형·위치·직전 와드 수)
 * @param contextMatched 그중 실제 데스 기록과 같은 것
 */
public record Verification(
        int timeClaims,
        int timeMatched,
        int numberClaims,
        int numberMatched,
        List<String> unmatched,
        int contextClaims,
        int contextMatched
) {

    /** 내용 검사가 없는 경우 (반복 실수 분석 등) */
    public Verification(int timeClaims, int timeMatched, int numberClaims, int numberMatched, List<String> unmatched) {
        this(timeClaims, timeMatched, numberClaims, numberMatched, unmatched, 0, 0);
    }

    /** 두 검사 결과 합치기 */
    public Verification plus(Verification other) {
        List<String> all = new java.util.ArrayList<>(unmatched);
        other.unmatched.stream().filter(u -> !all.contains(u)).forEach(all::add);
        return new Verification(timeClaims + other.timeClaims, timeMatched + other.timeMatched,
                numberClaims + other.numberClaims, numberMatched + other.numberMatched, all,
                contextClaims + other.contextClaims, contextMatched + other.contextMatched);
    }

    public boolean allMatched() {
        return unmatched.isEmpty();
    }

    /** 둘 중 어느 답이 나은지 비교용 (확인 안 된 개수가 적을수록 좋다) */
    public int problems() {
        return unmatched.size();
    }

    /** AI 에게 다시 쓰라고 할 때 붙이는 설명 */
    public String feedback() {
        StringBuilder sb = new StringBuilder("[이전 답변 검토 결과]\n데이터와 맞지 않는 내용이 있었다.\n");
        for (String u : unmatched) {
            sb.append("- ").append(u).append('\n');
        }
        sb.append("데이터에 있는 시각과 숫자만 그대로 써서 처음부터 다시 작성하라. 숫자를 새로 계산하지 않는다.");
        return sb.toString();
    }
}
