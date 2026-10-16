package com.topsiya.weakness;

import java.util.List;
import java.util.Map;

/** 약점 분석용 AI 지시문과 응답 형식 */
final class WeaknessPrompt {

    private WeaknessPrompt() {
    }

    static final String INSTRUCTIONS = """
            너는 리그 오브 레전드 라인전 전문 코치다.
            사용자가 보내는 JSON 은 한 플레이어의 최근 여러 경기(같은 포지션)를 서버가 집계한 통계다.
            이 통계를 보고 "여러 경기에 걸쳐 반복되는 습관"을 찾아 정리한다. 한 경기에서만 있었던 일은 약점으로 꼽지 않는다.

            [규칙]
            1. 통계에 있는 숫자만 근거로 쓴다. 숫자를 새로 계산하거나 지어내지 않는다.
            2. weaknesses 는 영향이 큰 순서로 최대 3개. 근거가 약하면 3개보다 적어도 된다.
               - title: 약점을 짧은 이름으로 (예: "시야 없이 라인을 미는 습관")
               - evidence: 통계 근거를 숫자와 함께 (예: "15분 전 데스 9번 중 7번이 죽기 전 2분 와드 0개")
               - frequency: 몇 경기에서 나타났는지 perGame 을 보고 쓴다 (예: "7경기 중 5경기")
               - fix: 다음 경기부터 바로 할 수 있는 구체적인 행동 한두 문장
            3. strengths 는 통계로 확인되는 강점 0~2개. 억지로 만들지 않는다.
            4. summary 는 전체 경향을 한두 문장으로.
            5. focus 는 다음 몇 경기 동안 딱 하나만 집중할 것을 한 문장으로.
            6. 한국어로 쓴다. 친절하되 돌려 말하지 않는다. 챔피언은 한국어 이름으로 부른다.

            [데이터 설명]
            - diff 값은 "나 - 상대". 음수면 뒤처졌다는 뜻이다.
            - gamesBehindInCsAt15: 15분에 CS 가 뒤처진 경기 수
            - deathsBefore15 / gankedDeathsBefore15 / deathsWithNoWardBefore15 / deathsInEnemyZoneBefore15:
              15분까지 데스 수 / 그중 갱 / 그중 직전 2분 와드 0개 / 그중 상대 진영에서
            - soloDeaths, soloKills: 15분까지 라인 상대와 1:1 데스·킬 합계
            - gamesLostOuterTowerFirst: 내 1차 포탑을 상대보다 먼저 잃은 경기 수
            - swingStarts: 경기마다 CS 격차가 가장 크게 벌어진 3분 구간이 몇 분대에 시작됐는지 분포
            - position 이 UTILITY 면 서포터, BOTTOM 이면 원딜 기준으로 해석한다. 서포터는 CS 보다 데스·시야를 중요하게 본다.
            """;

    private static final Map<String, Object> STRING = Map.of("type", "string");

    static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("summary", "weaknesses", "strengths", "focus"),
            "properties", Map.of(
                    "summary", STRING,
                    "weaknesses", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "additionalProperties", false,
                                    "required", List.of("title", "evidence", "frequency", "fix"),
                                    "properties", Map.of(
                                            "title", STRING,
                                            "evidence", STRING,
                                            "frequency", STRING,
                                            "fix", STRING
                                    )
                            )
                    ),
                    "strengths", Map.of("type", "array", "items", STRING),
                    "focus", STRING
            )
    );
}
