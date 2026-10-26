package com.topsiya.goal;

import com.topsiya.lane.LaneAnalysisException;
import com.topsiya.lane.LaneReport;
import com.topsiya.lane.LaneService;
import com.topsiya.riot.RiotApiClient;
import com.topsiya.riot.dto.MatchDto;
import com.topsiya.store.FileStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class GoalService {

    static final String CATEGORY = "goals";
    /** 목표를 정한 뒤 최근 몇 판까지 보여줄지 */
    static final int MAX_CHECKS = 5;
    /** 최근 몇 판 안에서 찾을지 */
    static final int LOOKBACK = 10;

    private final FileStore store;
    private final RiotApiClient riotApiClient;
    private final LaneService laneService;

    public GoalService(FileStore store, RiotApiClient riotApiClient, LaneService laneService) {
        this.store = store;
        this.riotApiClient = riotApiClient;
        this.laneService = laneService;
    }

    public GoalProgress set(String puuid, GoalRequest request) {
        if (request.metric() == null || request.target() == null) {
            throw new LaneAnalysisException("목표 지표와 목표값이 필요해요.");
        }
        long since = System.currentTimeMillis();
        if (request.sourceMatchId() != null && !request.sourceMatchId().isBlank()) {
            MatchDto source = riotApiClient.getMatch(request.sourceMatchId());
            if (source != null && source.info() != null && source.info().gameEndTimestamp() > 0) {
                since = source.info().gameEndTimestamp();
            }
        }
        Goal goal = new Goal(request.metric(), request.target(), request.text(), request.position(),
                request.sourceMatchId(), since, System.currentTimeMillis());
        store.write(CATEGORY, puuid, goal);
        return progress(puuid).orElseGet(() -> toProgress(goal, List.of()));
    }

    public void clear(String puuid) {
        store.delete(CATEGORY, puuid);
    }

    /** 목표가 없으면 빈 값 */
    public Optional<GoalProgress> progress(String puuid) {
        Optional<Goal> saved = store.read(CATEGORY, puuid, Goal.class);
        if (saved.isEmpty()) {
            return Optional.empty();
        }
        Goal goal = saved.get();

        List<GoalProgress.Check> checks = new ArrayList<>();
        for (String matchId : riotApiClient.getMatchIds(puuid, LOOKBACK)) {
            if (checks.size() >= MAX_CHECKS) break;
            if (matchId.equals(goal.sourceMatchId())) continue;

            MatchDto match = riotApiClient.getMatch(matchId);
            if (!playedAfterGoal(match, goal) || !samePosition(match, puuid, goal)) continue;

            LaneReport report;
            try {
                report = laneService.analyze(matchId, puuid);
            } catch (LaneAnalysisException e) {
                continue; // 맞라인이 없는 경기 등
            }
            GoalProgress.Check check = judge(goal, report);
            if (check != null) {
                checks.add(check);
            }
        }
        return Optional.of(toProgress(goal, checks));
    }

    static GoalProgress.Check judge(Goal goal, LaneReport report) {
        Integer value = goal.metric().measure(report);
        if (value == null) {
            return null;
        }
        return new GoalProgress.Check(report.matchId(), report.myChampion(), report.opponentChampion(),
                report.win(), value, goal.metric().achieved(value, goal.target()));
    }

    static GoalProgress toProgress(Goal goal, List<GoalProgress.Check> checks) {
        int achieved = (int) checks.stream().filter(GoalProgress.Check::achieved).count();
        return new GoalProgress(goal, goal.metric().describe(goal.target()), goal.metric().unit(), checks, achieved);
    }

    private static boolean playedAfterGoal(MatchDto match, Goal goal) {
        return match != null && match.info() != null && match.info().gameEndTimestamp() > goal.since();
    }

    private static boolean samePosition(MatchDto match, String puuid, Goal goal) {
        if (goal.position() == null || goal.position().isBlank()) {
            return true;
        }
        if (match.info().participants() == null) {
            return false;
        }
        return match.info().participants().stream()
                .filter(p -> puuid.equals(p.puuid()))
                .anyMatch(p -> goal.position().equals(p.teamPosition()));
    }
}
