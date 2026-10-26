package com.topsiya.goal;

/**
 * 사용자가 정한 "도전 중인 목표". 소환사(puuid)마다 하나만 둔다.
 *
 * @param text          목표 문장 (AI 가 쓴 "다음 판엔 이것만")
 * @param position      이 포지션 경기만 판정한다 (TOP 등). 비어 있으면 모든 포지션
 * @param sourceMatchId 목표를 만든 경기
 * @param since         이 시각(ms) 이후에 끝난 경기만 판정한다 = 목표를 만든 경기가 끝난 시각
 */
public record Goal(
        GoalMetric metric,
        int target,
        String text,
        String position,
        String sourceMatchId,
        long since,
        long createdAt
) {
}
