package com.topsiya.lane;

import com.topsiya.lane.LaneReport.Checkpoint;
import com.topsiya.lane.LaneReport.Death;
import com.topsiya.lane.LaneReport.MinutePoint;
import com.topsiya.lane.LaneReport.Swing;
import com.topsiya.riot.dto.MatchDto;
import com.topsiya.riot.dto.MatchDto.Participant;
import com.topsiya.riot.dto.TimelineDto;
import com.topsiya.riot.dto.TimelineDto.Event;
import com.topsiya.riot.dto.TimelineDto.Frame;
import com.topsiya.riot.dto.TimelineDto.ParticipantFrame;
import com.topsiya.riot.dto.TimelineDto.Position;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 경기 상세 + 타임라인 → 라인전 요약(LaneReport).
 *
 * 외부 API 를 부르지 않는 순수 계산 클래스라서 단위 테스트가 쉽다.
 */
public final class LaneAnalyzer {

    static final int LANE_PHASE_END_MINUTE = 15;
    static final int DETAIL_END_MINUTE = 20;
    static final int SWING_WINDOW_MINUTES = 3;
    static final long WARD_LOOKBACK_MS = 120_000;
    static final int[] CHECKPOINT_MINUTES = {5, 10, 15};

    private LaneAnalyzer() {
    }

    public static LaneReport analyze(MatchDto match, TimelineDto timeline, String myPuuid) {
        if (match == null || match.info() == null || timeline == null || timeline.info() == null) {
            throw new LaneAnalysisException("경기 데이터를 불러오지 못했어요.");
        }
        List<Participant> participants = match.info().participants();

        Participant me = participants.stream()
                .filter(p -> myPuuid.equals(p.puuid()))
                .findFirst()
                .orElseThrow(() -> new LaneAnalysisException("이 경기에서 소환사를 찾을 수 없어요."));

        String position = me.teamPosition();
        if (position == null || position.isBlank()) {
            throw new LaneAnalysisException("라인이 없는 게임 모드(칼바람 등)는 분석할 수 없어요.");
        }

        Participant opponent = participants.stream()
                .filter(p -> p.teamId() != me.teamId() && position.equals(p.teamPosition()))
                .findFirst()
                .orElseThrow(() -> new LaneAnalysisException("맞라인 상대를 찾을 수 없어요."));

        Map<Integer, Participant> byId = participants.stream()
                .collect(Collectors.toMap(Participant::participantId, Function.identity()));

        List<Frame> frames = timeline.info().frames() == null ? List.of() : timeline.info().frames();
        List<Event> events = frames.stream()
                .filter(f -> f.events() != null)
                .flatMap(f -> f.events().stream())
                .toList();

        int myId = me.participantId();
        int oppId = opponent.participantId();
        Set<Integer> laneEnemyIds = laneEnemyIds(participants, me);
        String laneType = laneTypeOf(position);

        List<MinutePoint> points = minutePoints(frames, myId, oppId);

        return new LaneReport(
                match.metadata() != null ? match.metadata().matchId() : null,
                position,
                me.win(),
                match.info().gameDuration(),
                me.championName(),
                opponent.championName(),
                checkpoints(frames, myId, oppId),
                points,
                biggestCsSwing(points),
                deaths(events, me, laneEnemyIds, byId),
                countSoloKills(events, myId, oppId),
                countSoloKills(events, oppId, myId),
                countPlates(events, laneType, otherTeam(me.teamId())),
                countPlates(events, laneType, me.teamId()),
                outerTowerTime(events, laneType, me.teamId()),
                outerTowerTime(events, laneType, otherTeam(me.teamId())),
                countWards(events, myId, 0, LANE_PHASE_END_MINUTE * 60_000L)
        );
    }

    // ---------------------------------------------------------------- 분 단위 비교

    static List<MinutePoint> minutePoints(List<Frame> frames, int myId, int oppId) {
        List<MinutePoint> points = new ArrayList<>();
        for (Frame frame : frames) {
            int minute = minuteOf(frame.timestamp());
            if (minute > DETAIL_END_MINUTE) break;
            ParticipantFrame mine = frameOf(frame, myId);
            ParticipantFrame theirs = frameOf(frame, oppId);
            if (mine == null || theirs == null) continue;
            points.add(new MinutePoint(
                    minute,
                    mine.cs(),
                    theirs.cs(),
                    mine.cs() - theirs.cs(),
                    mine.totalGold() - theirs.totalGold()
            ));
        }
        return points;
    }

    static List<Checkpoint> checkpoints(List<Frame> frames, int myId, int oppId) {
        List<Checkpoint> result = new ArrayList<>();
        for (int target : CHECKPOINT_MINUTES) {
            for (Frame frame : frames) {
                if (minuteOf(frame.timestamp()) != target) continue;
                ParticipantFrame mine = frameOf(frame, myId);
                ParticipantFrame theirs = frameOf(frame, oppId);
                if (mine == null || theirs == null) break;
                result.add(new Checkpoint(
                        target,
                        mine.cs(),
                        theirs.cs(),
                        mine.cs() - theirs.cs(),
                        mine.totalGold() - theirs.totalGold(),
                        mine.level() - theirs.level()
                ));
                break;
            }
        }
        return result;
    }

    /** SWING_WINDOW_MINUTES 동안 CS 격차가 가장 많이 줄어든 구간 */
    static Swing biggestCsSwing(List<MinutePoint> points) {
        Swing worst = null;
        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 1; j < points.size(); j++) {
                MinutePoint from = points.get(i);
                MinutePoint to = points.get(j);
                if (to.minute() - from.minute() > SWING_WINDOW_MINUTES) break;
                if (to.minute() - from.minute() != SWING_WINDOW_MINUTES) continue;
                int change = to.csDiff() - from.csDiff();
                if (change < 0 && (worst == null || change < worst.change())) {
                    worst = new Swing(from.minute(), to.minute(), change);
                }
            }
        }
        return worst;
    }

    // ---------------------------------------------------------------- 데스

    static List<Death> deaths(List<Event> events, Participant me, Set<Integer> laneEnemyIds,
                              Map<Integer, Participant> byId) {
        List<Death> result = new ArrayList<>();
        int myId = me.participantId();

        for (Event e : events) {
            if (!"CHAMPION_KILL".equals(e.type())) continue;
            if (e.victimId() == null || e.victimId() != myId) continue;
            if (e.timestamp() > DETAIL_END_MINUTE * 60_000L) continue;

            List<Integer> involved = new ArrayList<>();
            if (e.killerId() != null && e.killerId() > 0) involved.add(e.killerId());
            if (e.assistingParticipantIds() != null) involved.addAll(e.assistingParticipantIds());

            boolean ganked = involved.stream().anyMatch(id -> !laneEnemyIds.contains(id));

            List<String> assisters = e.assistingParticipantIds() == null ? List.of()
                    : e.assistingParticipantIds().stream().map(id -> championName(byId, id)).toList();

            result.add(new Death(
                    mmss(e.timestamp()),
                    e.timestamp() / 1000,
                    e.killerId() == null || e.killerId() == 0 ? "포탑/미니언" : championName(byId, e.killerId()),
                    assisters,
                    ganked,
                    zoneOf(e.position(), me.teamId()),
                    countWards(events, myId, e.timestamp() - WARD_LOOKBACK_MS, e.timestamp())
            ));
        }
        return result;
    }

    /** killerId 가 victimId 를 어시스트 없이 잡은 횟수 (라인전 구간) */
    static int countSoloKills(List<Event> events, int killerId, int victimId) {
        int count = 0;
        for (Event e : events) {
            if (!"CHAMPION_KILL".equals(e.type())) continue;
            if (e.timestamp() > LANE_PHASE_END_MINUTE * 60_000L) continue;
            boolean noAssist = e.assistingParticipantIds() == null || e.assistingParticipantIds().isEmpty();
            if (noAssist && e.killerId() != null && e.killerId() == killerId
                    && e.victimId() != null && e.victimId() == victimId) {
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- 포탑·와드

    /** ownerTeamId 팀이 내 라인에서 잃은 방패 수 */
    static int countPlates(List<Event> events, String laneType, int ownerTeamId) {
        if (laneType == null) return 0;
        return (int) events.stream()
                .filter(e -> "TURRET_PLATE_DESTROYED".equals(e.type()))
                .filter(e -> laneType.equals(e.laneType()))
                .filter(e -> e.teamId() != null && e.teamId() == ownerTeamId)
                .count();
    }

    /** ownerTeamId 팀의 내 라인 1차 포탑이 깨진 시각 */
    static String outerTowerTime(List<Event> events, String laneType, int ownerTeamId) {
        if (laneType == null) return null;
        return events.stream()
                .filter(e -> "BUILDING_KILL".equals(e.type()))
                .filter(e -> "TOWER_BUILDING".equals(e.buildingType()))
                .filter(e -> "OUTER_TURRET".equals(e.towerType()))
                .filter(e -> laneType.equals(e.laneType()))
                .filter(e -> e.teamId() != null && e.teamId() == ownerTeamId)
                .findFirst()
                .map(e -> mmss(e.timestamp()))
                .orElse(null);
    }

    static int countWards(List<Event> events, int creatorId, long fromMs, long toMs) {
        return (int) events.stream()
                .filter(e -> "WARD_PLACED".equals(e.type()))
                .filter(e -> e.creatorId() != null && e.creatorId() == creatorId)
                .filter(e -> !"UNDEFINED".equals(e.wardType()))
                .filter(e -> e.timestamp() >= fromMs && e.timestamp() <= toMs)
                .count();
    }

    // ---------------------------------------------------------------- 보조 함수

    /** 라인 상대로 볼 적: 바텀 듀오(원딜·서폿)는 둘 다, 나머지는 같은 포지션 한 명 */
    static Set<Integer> laneEnemyIds(List<Participant> participants, Participant me) {
        boolean bottomDuo = "BOTTOM".equals(me.teamPosition()) || "UTILITY".equals(me.teamPosition());
        Set<Integer> ids = new HashSet<>();
        for (Participant p : participants) {
            if (p.teamId() == me.teamId()) continue;
            boolean sameLane = bottomDuo
                    ? "BOTTOM".equals(p.teamPosition()) || "UTILITY".equals(p.teamPosition())
                    : me.teamPosition().equals(p.teamPosition());
            if (sameLane) ids.add(p.participantId());
        }
        return ids;
    }

    static String laneTypeOf(String position) {
        return switch (position) {
            case "TOP" -> "TOP_LANE";
            case "MIDDLE" -> "MID_LANE";
            case "BOTTOM", "UTILITY" -> "BOT_LANE";
            default -> null; // 정글은 라인 포탑 없음
        };
    }

    /**
     * 협곡을 대각선(강)으로 나눠 대략 어느 진영인지 판단한다.
     * 블루팀(100) 본진이 왼쪽 아래라서 x + y 가 작을수록 블루 진영.
     */
    static String zoneOf(Position pos, int myTeamId) {
        if (pos == null) return "알 수 없음";
        int sum = pos.x() + pos.y();
        boolean blueSide = sum < 13_800;
        boolean redSide = sum > 15_800;
        if (!blueSide && !redSide) return "강가";
        boolean mySide = (myTeamId == 100) == blueSide;
        return mySide ? "우리 진영" : "상대 진영";
    }

    private static int otherTeam(int teamId) {
        return teamId == 100 ? 200 : 100;
    }

    private static ParticipantFrame frameOf(Frame frame, int participantId) {
        if (frame.participantFrames() == null) return null;
        return frame.participantFrames().get(String.valueOf(participantId));
    }

    private static String championName(Map<Integer, Participant> byId, int participantId) {
        Participant p = byId.get(participantId);
        return p == null ? "알 수 없음" : p.championName();
    }

    static int minuteOf(long timestampMs) {
        return (int) Math.round(timestampMs / 60_000.0);
    }

    static String mmss(long timestampMs) {
        long totalSeconds = timestampMs / 1000;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
