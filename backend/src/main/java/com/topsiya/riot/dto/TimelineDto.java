package com.topsiya.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * MATCH-V5 타임라인 응답 중 탑시야에서 쓰는 필드만 받는다.
 *
 * frames: 1분(60,000ms)마다 찍힌 스냅샷. 0번 프레임은 게임 시작(0분).
 * 각 프레임의 events 에는 직전 1분 동안 일어난 일(킬, 와드, 포탑 등)이 시간순으로 들어 있다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TimelineDto(Info info) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Info(
            long frameInterval,
            List<Frame> frames,
            List<TimelineParticipant> participants
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TimelineParticipant(int participantId, String puuid) {
    }

    /**
     * @param participantFrames 키는 "1" ~ "10" (participantId 를 문자열로)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Frame(
            long timestamp,
            Map<String, ParticipantFrame> participantFrames,
            List<Event> events
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParticipantFrame(
            int participantId,
            Position position,
            int totalGold,
            int level,
            int xp,
            int minionsKilled,
            int jungleMinionsKilled
    ) {
        public int cs() {
            return minionsKilled + jungleMinionsKilled;
        }
    }

    /** 소환사의 협곡 좌표. 왼쪽 아래(블루 본진)가 (0,0), 오른쪽 위(레드 본진)가 약 (14800, 14800) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Position(int x, int y) {
    }

    /**
     * 이벤트 종류(type)에 따라 채워지는 필드가 다르다. 없는 필드는 null.
     * <ul>
     *   <li>CHAMPION_KILL: killerId, victimId, assistingParticipantIds, position</li>
     *   <li>WARD_PLACED: creatorId, wardType</li>
     *   <li>BUILDING_KILL: killerId, teamId(포탑을 잃은 팀), buildingType, laneType, towerType</li>
     *   <li>TURRET_PLATE_DESTROYED: killerId, teamId(방패를 잃은 팀), laneType</li>
     * </ul>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(
            String type,
            long timestamp,
            Integer participantId,
            Integer killerId,
            Integer victimId,
            List<Integer> assistingParticipantIds,
            Position position,
            Integer creatorId,
            String wardType,
            Integer teamId,
            String buildingType,
            String laneType,
            String towerType
    ) {
    }
}
