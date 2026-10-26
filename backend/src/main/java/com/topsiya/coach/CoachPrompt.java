package com.topsiya.coach;

import com.topsiya.goal.SuggestedGoal;

import java.util.List;
import java.util.Map;

/** AI 에게 주는 지시문과 응답 형식(JSON 스키마) */
final class CoachPrompt {

    private CoachPrompt() {
    }

    static final String INSTRUCTIONS = """
            너는 리그 오브 레전드 라인전 전문 코치다.
            사용자가 보내는 JSON 은 한 경기의 라인전 요약 데이터다. 이 데이터만 보고 코칭 리포트를 쓴다.

            [규칙]
            1. JSON 에 있는 수치와 사건만 근거로 쓴다. 스킬 적중, 채팅, 아이템, 룬처럼 데이터에 없는 내용은 추측해서 단정하지 않는다.
            2. mistakes 는 경기에 영향이 큰 순서로 1~3개만 쓴다.
               - time: "13:20" 처럼 시각, 또는 "12~15분" 처럼 구간
               - what: 무슨 일이 있었는지 한 문장
               - why: 데이터 근거를 수치와 함께 (예: "직전 2분 와드 0개, 정글 바이가 합류")
               - fix: 다음 경기에서 바로 할 수 있는 구체적인 행동 한두 문장
            3. goodPoints 는 데이터로 확인되는 잘한 점 0~2개. 억지로 만들지 않는다.
            4. summary 는 라인전 흐름을 한두 문장으로 요약한다.
            5. nextGameTip 은 다음 경기에 기억할 한 가지를 한 문장으로 쓴다.
            6. 한국어로 쓴다. 친절하되 돌려 말하지 않는다. 챔피언은 한국어 이름으로 부른다 (예: Darius → 다리우스).

            [데이터 설명]
            - csDiff, goldDiff, levelDiff 는 "나 - 상대" 값이다. 양수면 내가 앞섰고 음수면 뒤처졌다.
            - checkpoints 는 5·10·15분 시점 비교, timeline 은 분 단위 추이다.
            - biggestCsSwing 은 3분 동안 CS 격차가 가장 크게 줄어든 구간이다 (change 가 음수일수록 손해).
            - deaths[].type 은 데스 유형이다. 유형을 정확히 구분해서 쓴다.
                · "라인 교전": 라인 상대만 참여
                · "정글 갱": 상대 정글이 와서 잡힘 (다른 라이너 없음)
                · "상대 로밍": 정글이 아니라 다른 라인 라이너가 내려와서 잡힘
                · "정글+로밍": 정글과 다른 라이너가 함께 옴
                · "한타": 적 4명 이상 참여
              로밍을 "갱"이라고 부르지 않는다. 로밍 데스는 상대 라이너가 라인에서 사라진 것을 놓친 문제로, 정글 갱은 정글 동선을 놓친 문제로 코칭한다.
            - deaths[].myWardsLast2Min 은 죽기 전 2분 동안 내가 설치한 와드 수다.
            - deaths[].zone 은 사망 위치를 대략 나눈 것이다 (우리 진영 / 강가 / 상대 진영).
            - platesTaken/platesLost 는 내 라인 포탑 방패 획득/손실 수다.
            - position 이 TOP 이 아니면 그 포지션 기준으로 코칭한다 (UTILITY = 서포터, BOTTOM = 원딜).

            """ + SuggestedGoal.PROMPT_GUIDE;

    private static final Map<String, Object> STRING = Map.of("type", "string");

    /** Structured Outputs: AI 응답이 반드시 이 구조의 JSON 이 되도록 강제한다 */
    static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("summary", "mistakes", "goodPoints", "nextGameTip", "goal"),
            "properties", Map.of(
                    "summary", STRING,
                    "mistakes", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "additionalProperties", false,
                                    "required", List.of("time", "what", "why", "fix"),
                                    "properties", Map.of(
                                            "time", STRING,
                                            "what", STRING,
                                            "why", STRING,
                                            "fix", STRING
                                    )
                            )
                    ),
                    "goodPoints", Map.of("type", "array", "items", STRING),
                    "nextGameTip", STRING,
                    "goal", SuggestedGoal.schema()
            )
    );
}
