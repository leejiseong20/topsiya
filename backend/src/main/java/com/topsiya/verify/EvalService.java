package com.topsiya.verify;

import com.topsiya.coach.CoachProbe;
import com.topsiya.coach.CoachService;
import com.topsiya.coach.CoachingException;
import com.topsiya.coach.CoachingReport;
import com.topsiya.lane.LaneAnalysisException;
import com.topsiya.match.MatchService;
import com.topsiya.match.MatchSummary;
import com.topsiya.riot.RiotApiClient;
import com.topsiya.store.FileStore;
import com.topsiya.weakness.WeaknessReport;
import com.topsiya.weakness.WeaknessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** AI 답이 실제 데이터와 얼마나 맞는지 모아서 보여준다 (발표용 정확도 측정) */
@Service
public class EvalService {

    private static final Logger log = LoggerFactory.getLogger(EvalService.class);
    static final int MAX_RUN = 20;

    private final FileStore store;
    private final RiotApiClient riotApiClient;
    private final CoachService coachService;

    public EvalService(FileStore store, RiotApiClient riotApiClient, CoachService coachService) {
        this.store = store;
        this.riotApiClient = riotApiClient;
        this.coachService = coachService;
    }

    /** 코칭 저장 파일 이름 = 경기ID_puuid */
    private static final Pattern COACH_KEY = Pattern.compile("^([A-Z0-9]+_\\d+)_(.+)$");

    /**
     * 저장된 모든 AI 답의 검증 통계.
     * 경기별 코칭의 최종 답은 지금 검사 규칙으로 다시 검증한다 (규칙이 엄격해져도 바로 반영).
     * 첫 답은 글이 저장돼 있지 않아서, 만들 때 기록한 결과를 쓴다.
     */
    public Map<String, EvalSummary> summary() {
        List<Verification[]> coaching = new ArrayList<>();
        for (String key : store.keys(CoachService.CATEGORY)) {
            Optional<CoachingReport> saved = store.read(CoachService.CATEGORY, key, CoachingReport.class);
            if (saved.isEmpty()) continue;
            CoachingReport r = saved.get();
            Verification now = recheck(key, r);
            if (now == null) now = r.verification();
            if (now != null) coaching.add(new Verification[]{now, r.firstDraft()});
        }
        List<Verification[]> weakness = new ArrayList<>();
        for (String key : store.keys(WeaknessService.CATEGORY)) {
            store.read(WeaknessService.CATEGORY, key, WeaknessReport.class)
                    .filter(r -> r.verification() != null)
                    .ifPresent(r -> weakness.add(new Verification[]{r.verification(), r.firstDraft()}));
        }
        return Map.of("coaching", EvalSummary.of(coaching), "weakness", EvalSummary.of(weakness));
    }

    /** 검사기 시험: 저장된 코칭마다 틀린 내용 6종을 하나씩 심어 보고 몇 개를 잡는지 센다 */
    public ProbeSummary probe() {
        Map<String, int[]> byType = new LinkedHashMap<>(); // 유형 → {심음, 잡음, 느슨한 검사로 잡음}
        int reports = 0;
        for (String key : store.keys(CoachService.CATEGORY)) {
            Matcher m = COACH_KEY.matcher(key);
            Optional<CoachingReport> saved = store.read(CoachService.CATEGORY, key, CoachingReport.class);
            if (!m.matches() || saved.isEmpty()) continue;
            List<CoachProbe.Result> results;
            try {
                results = coachService.probe(m.group(1), m.group(2), saved.get());
            } catch (RuntimeException e) {
                continue; // 원본 경기 데이터가 없는 경우 등
            }
            reports++;
            for (CoachProbe.Result r : results) {
                int[] row = byType.computeIfAbsent(r.type(), t -> new int[3]);
                row[0]++;
                if (r.caught()) row[1]++;
                if (r.caughtLoose()) row[2]++;
            }
        }
        List<ProbeSummary.Row> rows = new ArrayList<>();
        int planted = 0, caught = 0, loose = 0;
        for (var e : byType.entrySet()) {
            int[] v = e.getValue();
            rows.add(new ProbeSummary.Row(e.getKey(), v[0], v[1], v[2]));
            planted += v[0];
            caught += v[1];
            loose += v[2];
        }
        return new ProbeSummary(reports, planted, caught, loose, rows);
    }

    private Verification recheck(String key, CoachingReport report) {
        Matcher m = COACH_KEY.matcher(key);
        if (!m.matches()) return null;
        try {
            return coachService.recheck(m.group(1), m.group(2), report);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 최근 경기들에 AI 코칭을 한꺼번에 돌려서 측정 데이터를 만든다.
     * 이미 코칭한 경기는 저장본을 쓰므로 AI 를 다시 부르지 않는다.
     *
     * @return 이번에 처리한 경기 수
     */
    public int run(String puuid, int count) {
        int safeCount = Math.max(1, Math.min(count, MAX_RUN));
        int done = 0;
        for (String matchId : riotApiClient.getMatchIds(puuid, safeCount)) {
            MatchSummary summary = MatchService.toSummary(riotApiClient.getMatch(matchId), puuid);
            if (summary == null || summary.opponentChampionName() == null) continue; // 맞라인 없는 경기
            try {
                coachService.coach(matchId, puuid);
                done++;
            } catch (LaneAnalysisException | CoachingException e) {
                log.warn("측정 중 건너뜀 {}: {}", matchId, e.getMessage());
            }
        }
        return done;
    }
}
