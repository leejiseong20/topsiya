package com.topsiya.verify;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 저장된 AI 답 전체의 검증 통계 (정확도 측정 결과).
 *
 * @param reports        검증 기록이 있는 AI 답 수
 * @param rewritten      첫 답이 데이터와 안 맞아서 다시 쓰게 한 수
 * @param firstDraft     AI 첫 답 기준 (검증 전)
 * @param afterCheck     최종 답 기준 (검증·재작성 후)
 * @param fullyMatched   최종 답에서 확인 안 된 내용이 하나도 없는 답 수
 */
public record EvalSummary(
        int reports,
        int rewritten,
        Totals firstDraft,
        Totals afterCheck,
        int fullyMatched
) {

    public record Totals(int timeClaims, int timeMatched, int numberClaims, int numberMatched,
                         int contextClaims, int contextMatched) {

        static final Totals ZERO = new Totals(0, 0, 0, 0, 0, 0);

        /** 계산값이라 JSON 에 넣으려면 이름을 직접 붙여야 한다 */
        @JsonProperty("timeRate")
        public Double timeRate() {
            return timeClaims == 0 ? null : round(100.0 * timeMatched / timeClaims);
        }

        @JsonProperty("numberRate")
        public Double numberRate() {
            return numberClaims == 0 ? null : round(100.0 * numberMatched / numberClaims);
        }

        @JsonProperty("contextRate")
        public Double contextRate() {
            return contextClaims == 0 ? null : round(100.0 * contextMatched / contextClaims);
        }

        @JsonProperty("overallRate")
        public Double overallRate() {
            int claims = timeClaims + numberClaims + contextClaims;
            return claims == 0 ? null : round(100.0 * (timeMatched + numberMatched + contextMatched) / claims);
        }

        Totals plus(Verification v) {
            return new Totals(timeClaims + v.timeClaims(), timeMatched + v.timeMatched(),
                    numberClaims + v.numberClaims(), numberMatched + v.numberMatched(),
                    contextClaims + v.contextClaims(), contextMatched + v.contextMatched());
        }

        private static double round(double v) {
            return Math.round(v * 10) / 10.0;
        }
    }

    public static EvalSummary of(java.util.List<Verification[]> pairs) {
        Totals first = Totals.ZERO;
        Totals after = Totals.ZERO;
        int rewritten = 0;
        int full = 0;
        for (Verification[] pair : pairs) { // {최종, 첫 답(null 가능)}
            Verification finalCheck = pair[0];
            Verification draft = pair[1] != null ? pair[1] : finalCheck;
            if (pair[1] != null) rewritten++;
            if (finalCheck.allMatched()) full++;
            first = first.plus(draft);
            after = after.plus(finalCheck);
        }
        return new EvalSummary(pairs.size(), rewritten, first, after, full);
    }
}
