package com.topsiya.weakness;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.topsiya.goal.SuggestedGoal;
import com.topsiya.verify.Verification;

import java.util.List;

/**
 * 약점 분석 응답 = 서버가 계산한 통계(stats) + AI 해석(ai).
 * 화면에서 숫자는 stats 를, 문장은 ai 를 쓴다.
 * verification·firstDraft: AI 글 속 숫자를 통계와 대조한 결과 (서버가 채운다)
 */
public record WeaknessReport(WeaknessStats stats, AiPart ai, Verification verification, Verification firstDraft) {

    /** AI 가 만든 부분. 구조는 WeaknessPrompt.RESPONSE_SCHEMA 와 같다 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiPart(
            String summary,
            List<Weakness> weaknesses,
            List<String> strengths,
            String focus,
            /* focus 를 다음 경기들에서 숫자로 확인하기 위한 지표 */
            SuggestedGoal goal
    ) {
    }

    /**
     * @param title     약점 이름 (예: "시야 없이 라인을 미는 습관")
     * @param evidence  통계 근거 (예: "15분 전 데스 9번 중 7번이 직전 와드 0개")
     * @param frequency 몇 경기에서 나타났는지 (예: "8경기 중 6경기")
     * @param fix       고치는 방법
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Weakness(String title, String evidence, String frequency, String fix) {
    }
}
