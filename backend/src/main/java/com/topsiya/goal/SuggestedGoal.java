package com.topsiya.goal;

/** AI 가 제안한 목표: 어떤 지표를 몇까지 (예: WARDLESS_DEATHS_BEFORE_15, 0) */
public record SuggestedGoal(GoalMetric metric, int target) {

    /** AI 응답 스키마의 goal 부분 (CoachPrompt·WeaknessPrompt 가 같이 쓴다) */
    public static java.util.Map<String, Object> schema() {
        return java.util.Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", java.util.List.of("metric", "target"),
                "properties", java.util.Map.of(
                        "metric", java.util.Map.of(
                                "type", "string",
                                "enum", java.util.Arrays.stream(GoalMetric.values()).map(Enum::name).toList()),
                        "target", java.util.Map.of("type", "integer")
                )
        );
    }

    /** AI 지시문에 덧붙일 설명 */
    public static final String PROMPT_GUIDE = """
            [goal 고르는 법]
            goal 은 "다음 판에 할 한 가지"를 다음 경기 데이터로 지켰는지 확인하기 위한 지표와 목표값이다.
            아래 지표 중 그 한 가지와 가장 직접 연결되는 것 하나를 고르고, target 은 현실적으로 지킬 수 있는 값으로 정한다.
            (데스 지표는 보통 0 또는 1, CS 차이는 지금보다 조금 나은 값, 와드는 지금보다 조금 많은 값)
            """ + GoalMetric.promptList() + "\n";
}
