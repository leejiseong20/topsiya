package com.topsiya.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * MATCH-V5 경기 상세 응답 중 탑시야에서 쓰는 필드만 받는다.
 * (실제 응답에는 수백 개 필드가 있지만 나머지는 무시)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchDto(Metadata metadata, Info info) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(String matchId, List<String> participants) {
    }

    /**
     * @param gameDuration     경기 시간(초)
     * @param gameEndTimestamp 경기 종료 시각 (epoch milliseconds)
     * @param queueId          게임 종류 (420 솔로랭크, 440 자유랭크, 450 칼바람 등)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Info(
            long gameDuration,
            long gameEndTimestamp,
            int queueId,
            List<Participant> participants
    ) {
    }

    /**
     * @param teamPosition 라인 (TOP, JUNGLE, MIDDLE, BOTTOM, UTILITY). 칼바람 등은 빈 문자열
     * @param teamId       100 = 블루팀, 200 = 레드팀
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Participant(
            String puuid,
            String championName,
            String teamPosition,
            int teamId,
            boolean win,
            int kills,
            int deaths,
            int assists,
            int totalMinionsKilled,
            int neutralMinionsKilled,
            int champLevel
    ) {
    }
}
