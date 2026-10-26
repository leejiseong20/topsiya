package com.topsiya.coach;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.topsiya.goal.SuggestedGoal;
import com.topsiya.verify.Verification;

import java.util.List;

/** AI 가 만든 코칭 리포트. 구조는 CoachPrompt.RESPONSE_SCHEMA 와 같다 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoachingReport(
        String summary,
        List<Mistake> mistakes,
        List<String> goodPoints,
        String nextGameTip,
        /* nextGameTip 을 다음 경기에서 숫자로 확인하기 위한 지표 (예전에 저장된 리포트는 null) */
        SuggestedGoal goal,
        /* 아래 둘은 AI 가 아니라 서버가 채운다: 경기 데이터와 대조한 결과 */
        Verification verification,
        Verification firstDraft
) {

    CoachingReport withChecks(Verification verification, Verification firstDraft) {
        return new CoachingReport(summary, mistakes, goodPoints, nextGameTip, goal, verification, firstDraft);
    }

    /**
     * @param time 실수 시점 ("13:20" 또는 "12~15분")
     * @param what 무슨 일이 있었는지
     * @param why  데이터 근거 (수치 포함)
     * @param fix  다음 경기에 할 구체적인 행동
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Mistake(String time, String what, String why, String fix) {
    }
}
