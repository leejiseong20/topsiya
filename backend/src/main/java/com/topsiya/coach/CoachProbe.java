package com.topsiya.coach;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.topsiya.lane.LaneReport;
import com.topsiya.lane.LaneReport.Death;
import com.topsiya.verify.Verification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;

/**
 * 검사기 시험: 실제 AI 답에 "일부러 틀린 내용"을 하나씩 심고, 검사기가 잡아내는지 본다.
 * (원래 답이 100% 일치로 나와도, 검사기가 틀린 내용을 실제로 잡을 수 있다는 근거)
 *
 * 심는 실수 6종: 시각 / 데스 유형 / 데스 위치 / 와드 수 / CS 숫자 / 데스 수
 */
public final class CoachProbe {

    /**
     * @param caught      지금 검사기(엄격)가 잡았는지
     * @param caughtLoose 예전 느슨한 검사(시각엔 아무 사건, 숫자는 데이터 어딘가)였다면 잡았는지 → 비교용
     */
    public record Result(String type, boolean caught, boolean caughtLoose, String before, String after) {
    }

    private static final String[] TYPE_WORDS = {"정글+로밍", "정글 갱", "상대 로밍", "라인 교전", "소규모 교전", "한타"};

    private CoachProbe() {
    }

    public static List<Result> run(LaneReport lane, CoachingReport original, ObjectMapper objectMapper) {
        int baseProblems = CoachEvidence.verify(lane, original, objectMapper).problems();
        int baseLoose = CoachEvidence.verifyLoose(lane, original, objectMapper).problems();
        List<Result> results = new ArrayList<>();
        for (Mutation m : mutations(lane, original)) {
            Verification v = CoachEvidence.verify(lane, m.report, objectMapper);
            Verification loose = CoachEvidence.verifyLoose(lane, m.report, objectMapper);
            results.add(new Result(m.type, v.problems() > baseProblems, loose.problems() > baseLoose, m.before, m.after));
        }
        return results;
    }

    private record Mutation(String type, CoachingReport report, String before, String after) {
    }

    static List<Mutation> mutations(LaneReport lane, CoachingReport r) {
        List<Mutation> out = new ArrayList<>();
        List<CoachingReport.Mistake> mistakes = r.mistakes() == null ? List.of() : r.mistakes();

        // 실수 하나에 시각이 하나이고 그 시각에 데스가 있는 것
        for (int i = 0; i < mistakes.size(); i++) {
            CoachingReport.Mistake m = mistakes.get(i);
            String unit = m.time() + " " + m.what() + " " + m.why();
            Set<String> clocks = new java.util.LinkedHashSet<>();
            Matcher c = CoachContextCheck.CLOCK.matcher(unit);
            while (c.find()) clocks.add(c.group());
            if (clocks.size() != 1) continue;
            String clock = clocks.iterator().next();
            Death death = CoachContextCheck.deathNear(lane, CoachContextCheck.toSeconds(clock));
            if (death == null) continue;

            if (!has(out, "시각")) {
                String wrong = emptyClock(lane, death.seconds());
                if (wrong != null) {
                    out.add(new Mutation("시각", replaceAll(r, clock, wrong), clock, wrong));
                }
            }
            if (!has(out, "데스 유형")) {
                for (String word : TYPE_WORDS) {
                    if ((m.what() + m.why()).contains(word)) {
                        String wrong = "라인 교전".equals(death.type()) ? "정글 갱" : "라인 교전";
                        if (!wrong.equals(word)) {
                            out.add(new Mutation("데스 유형", replaceInMistake(r, i, t -> t.replace(word, wrong)), word, wrong));
                        }
                        break;
                    }
                }
            }
            if (!has(out, "데스 위치")) {
                for (String zone : CoachContextCheck.ZONES) {
                    if ((m.what() + m.why()).contains(zone) && zone.equals(death.zone())) {
                        String wrong = zone.equals("상대 진영") ? "우리 진영" : "상대 진영";
                        out.add(new Mutation("데스 위치", replaceInMistake(r, i, t -> t.replace(zone, wrong)), zone, wrong));
                        break;
                    }
                }
            }
            if (!has(out, "와드 수") && CoachContextCheck.BEFORE_DEATH.matcher(unit).find()) {
                for (int field = 0; field < 2 && !has(out, "와드 수"); field++) {
                    String text = field == 0 ? m.what() : m.why();
                    Matcher w = CoachContextCheck.WARD_NUMBER.matcher(text);
                    if (w.find()) {
                        int value = Integer.parseInt(w.group(1));
                        String wrong = String.valueOf(value + 2);
                        String changed = text.substring(0, w.start(1)) + wrong + text.substring(w.end(1));
                        final int f = field;
                        out.add(new Mutation("와드 수", replaceMistakeField(r, i, f, changed), w.group(1) + "개", wrong + "개"));
                    }
                }
            }
        }

        // 요약·잘한 점의 숫자
        List<String> texts = new ArrayList<>();
        texts.add(r.summary());
        if (r.goodPoints() != null) texts.addAll(r.goodPoints());
        for (int t = 0; t < texts.size(); t++) {
            String text = texts.get(t);
            if (text == null) continue;
            if (!has(out, "CS 숫자")) {
                for (String clause : CoachContextCheck.CLAUSE_SPLIT.split(text)) {
                    if (!clause.contains("CS")) continue;
                    Matcher n = java.util.regex.Pattern.compile("[+\\-−]?(\\d+)").matcher(clause);
                    while (n.find()) {
                        if (CoachContextCheck.insideSkip(clause, n.start())) continue;
                        String wrong = notIn(CoachContextCheck.csValues(lane), Integer.parseInt(n.group(1)));
                        String changedClause = clause.substring(0, n.start(1)) + wrong + clause.substring(n.end(1));
                        out.add(new Mutation("CS 숫자", replaceText(r, t, text.replace(clause, changedClause)),
                                n.group(), n.group().replace(n.group(1), wrong)));
                        break;
                    }
                    if (has(out, "CS 숫자")) break;
                }
            }
            if (!has(out, "데스 수")) {
                Matcher d = CoachContextCheck.DEATH_COUNT.matcher(text);
                if (d.find()) {
                    int g = d.group(1) != null ? 1 : 2;
                    if (!CoachContextCheck.insideSkip(text, d.start(g))) {
                        String wrong = notIn(CoachContextCheck.deathCounts(lane), Integer.parseInt(d.group(g)));
                        out.add(new Mutation("데스 수", replaceText(r, t,
                                text.substring(0, d.start(g)) + wrong + text.substring(d.end(g))), d.group(g), wrong));
                    }
                }
            }
        }
        return out;
    }

    // ---------- 도우미 ----------

    private static boolean has(List<Mutation> list, String type) {
        return list.stream().anyMatch(m -> m.type.equals(type));
    }

    /** 앞뒤 1분 안에 아무 사건도 없는 시각 (데스 2~8분 뒤 또는 앞) */
    private static String emptyClock(LaneReport lane, long from) {
        List<Long> events = new ArrayList<>();
        lane.deaths().forEach(d -> events.add(d.seconds()));
        for (String t : new String[]{lane.myOuterTowerLostAt(), lane.enemyOuterTowerDestroyedAt()}) {
            if (t != null && t.contains(":")) events.add(CoachContextCheck.toSeconds(t));
        }
        for (int k = 2; k <= 8; k++) {
            for (long candidate : new long[]{from + k * 60L, from - k * 60L}) {
                if (candidate < 60 || candidate > lane.gameDurationSeconds()) continue;
                boolean free = events.stream().allMatch(e -> Math.abs(e - candidate) > 60);
                if (free) return String.format("%d:%02d", candidate / 60, candidate % 60);
            }
        }
        return null;
    }

    private static String notIn(Set<Integer> allowed, int value) {
        for (int delta : new int[]{7, 9, 11, 13, 17, 23, 29}) {
            if (!allowed.contains(value + delta)) return String.valueOf(value + delta);
        }
        return String.valueOf(value + 97);
    }

    private static CoachingReport replaceAll(CoachingReport r, String from, String to) {
        UnaryOperator<String> f = s -> s == null ? null : s.replace(from, to);
        return new CoachingReport(f.apply(r.summary()),
                r.mistakes().stream().map(m -> new CoachingReport.Mistake(
                        f.apply(m.time()), f.apply(m.what()), f.apply(m.why()), f.apply(m.fix()))).toList(),
                r.goodPoints() == null ? null : r.goodPoints().stream().map(f).toList(),
                f.apply(r.nextGameTip()), r.goal(), null, null);
    }

    private static CoachingReport replaceInMistake(CoachingReport r, int index, UnaryOperator<String> f) {
        List<CoachingReport.Mistake> list = new ArrayList<>(r.mistakes());
        CoachingReport.Mistake m = list.get(index);
        list.set(index, new CoachingReport.Mistake(m.time(), f.apply(m.what()), f.apply(m.why()), m.fix()));
        return new CoachingReport(r.summary(), list, r.goodPoints(), r.nextGameTip(), r.goal(), null, null);
    }

    private static CoachingReport replaceMistakeField(CoachingReport r, int index, int field, String value) {
        List<CoachingReport.Mistake> list = new ArrayList<>(r.mistakes());
        CoachingReport.Mistake m = list.get(index);
        list.set(index, field == 0
                ? new CoachingReport.Mistake(m.time(), value, m.why(), m.fix())
                : new CoachingReport.Mistake(m.time(), m.what(), value, m.fix()));
        return new CoachingReport(r.summary(), list, r.goodPoints(), r.nextGameTip(), r.goal(), null, null);
    }

    /** index 0 = 요약, 1~ = 잘한 점 */
    private static CoachingReport replaceText(CoachingReport r, int index, String value) {
        if (index == 0) {
            return new CoachingReport(value, r.mistakes(), r.goodPoints(), r.nextGameTip(), r.goal(), null, null);
        }
        List<String> good = new ArrayList<>(r.goodPoints());
        good.set(index - 1, value);
        return new CoachingReport(r.summary(), r.mistakes(), good, r.nextGameTip(), r.goal(), null, null);
    }
}
