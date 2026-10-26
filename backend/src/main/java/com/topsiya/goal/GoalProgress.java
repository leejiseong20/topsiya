package com.topsiya.goal;

import java.util.List;

/**
 * 목표와, 목표를 정한 뒤 한 경기들의 판정 결과.
 *
 * @param criterion 판정 기준 문장 (예: "15분 전 와드 없이 죽은 횟수 0번 이하")
 * @param unit      값의 단위 (번 / 개)
 * @param checks    최신 경기부터. 아직 한 판도 안 했으면 빈 목록
 */
public record GoalProgress(
        Goal goal,
        String criterion,
        String unit,
        List<Check> checks,
        int achievedCount
) {

    public record Check(
            String matchId,
            String myChampion,
            String opponentChampion,
            boolean win,
            int value,
            boolean achieved
    ) {
    }
}
