package com.topsiya.coach;

import com.topsiya.lane.LaneReport;
import com.topsiya.lane.LaneReport.Death;
import com.topsiya.verify.Verification;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 엄격한 검사: "무엇에 대한" 시각·숫자인지까지 짝지어서 확인한다.
 *
 *  1) 시각 + 내용: 실수 하나(시각·무엇·왜)에 시각이 하나만 있고 그 시각에 데스가 있으면,
 *     글에 쓴 데스 유형(정글 갱·로밍·라인 교전·한타), 위치(상대 진영 등), 직전 와드 수가 그 데스 기록과 같아야 한다.
 *  2) 이름표 붙은 숫자: "와드 12개", "방패 3개", "3데스", "CS 격차 -5", "골드 300" 은
 *     같은 종류의 데이터 값과 맞아야 한다. (데이터 어딘가에 있는 숫자면 통과하던 느슨한 검사보다 엄격)
 *
 * 다음 판 조언(fix, nextGameTip)은 "앞으로 할 일"이라 사실 검사에서 뺀다.
 * 여기서 확인한 숫자는 지워서(공백) 느슨한 검사(ClaimVerifier)가 다시 세지 않게 한다.
 */
final class CoachContextCheck {

    static final long TOLERANCE_SECONDS = 30;

    static final Pattern CLOCK = Pattern.compile("(?<!\\d)(\\d{1,2}):(\\d{2})(?!\\d)");
    static final Pattern SKIP = Pattern.compile(
            "(?<!\\d)\\d{1,2}:\\d{2}(?!\\d)"                          // 13:20
                    + "|\\d{1,2}\\s*(?:~|-|–|부터)\\s*\\d{1,2}\\s*분"   // 12~15분
                    + "|\\d{1,2}\\s*분"                                // 15분 전
                    + "|\\d+\\s*(?:차|대\\s*\\d+)|1:1");                // 1차 포탑, 1대1
    private static final Pattern NUMBER = Pattern.compile("[+\\-−]?\\d+");
    static final Pattern WARD_NUMBER = Pattern.compile("와드[^.\\d]{0,12}?(\\d+)\\s*개");
    private static final Pattern WARD_NONE = Pattern.compile("와드\\s*(?:없이|가\\s*없|도\\s*없|를\\s*안)");
    private static final Pattern PLATE_NUMBER = Pattern.compile("방패[^.\\d]{0,10}?(\\d+)\\s*개");
    static final Pattern DEATH_COUNT = Pattern.compile(
            "(\\d+)\\s*(?:데스|번\\s*(?:사망|죽))|(?:데스|사망)[^.\\d]{0,6}?(\\d+)\\s*(?:번|회)");
    private static final Pattern GOLD_NUMBER = Pattern.compile("골드[^.\\d]{0,8}?([+\\-−]?\\d+)|([+\\-−]?\\d+)\\s*골드");
    static final Pattern BEFORE_DEATH = Pattern.compile("직전|죽기\\s*전|사망\\s*전");
    private static final Pattern JUNGLE_CLAIM = Pattern.compile("정글\\s*갱|정글\\+로밍|정글러?[이가]?\\s*(?:합류|개입|와서|함께|가세)|(?<!정글\\s?)갱");
    static final Pattern CLAUSE_SPLIT = Pattern.compile(",\\s*|(?<=고|며|지만|는데|으나)\\s+");
    private static final Pattern DEATH_WORDS = Pattern.compile("사망|죽|데스|직전");
    static final String[] ZONES = {"상대 진영", "우리 진영", "강가"};

    record Result(Verification verification, List<String> rest) {
    }

    private CoachContextCheck() {
    }

    static Result check(LaneReport lane, CoachingReport report) {
        Acc acc = new Acc();
        List<String> rest = new ArrayList<>();

        if (report.mistakes() != null) {
            for (CoachingReport.Mistake m : report.mistakes()) {
                StringBuilder unit = new StringBuilder(join(m.time(), m.what(), m.why()));
                deathContext(lane, unit, acc);
                labeledNumbers(lane, unit, acc);
                rest.add(unit.toString());
                rest.add(m.fix()); // 조언: 느슨한 검사만
            }
        }
        // 요약은 한 문장에 여러 사건이 섞이므로 "~했고", "~지만" 단위 구절마다 시각+내용을 본다
        for (String sentence : sentences(report.summary())) {
            for (String clause : CLAUSE_SPLIT.split(sentence)) {
                StringBuilder unit = new StringBuilder(clause);
                deathContext(lane, unit, acc);
                labeledNumbers(lane, unit, acc);
                rest.add(unit.toString());
            }
        }
        if (report.goodPoints() != null) {
            for (String good : report.goodPoints()) {
                StringBuilder unit = new StringBuilder(good == null ? "" : good);
                labeledNumbers(lane, unit, acc);
                rest.add(unit.toString());
            }
        }
        rest.add(report.nextGameTip());

        Verification v = new Verification(0, 0, acc.numberClaims, acc.numberMatched,
                new ArrayList<>(acc.unmatched), acc.contextClaims, acc.contextMatched);
        return new Result(v, rest);
    }

    // ---------- 1) 시각 + 내용 ----------

    private static void deathContext(LaneReport lane, StringBuilder unit, Acc acc) {
        String text = unit.toString();
        Set<String> clocks = new LinkedHashSet<>();
        Matcher m = CLOCK.matcher(text);
        while (m.find()) clocks.add(m.group());
        if (clocks.size() != 1) return; // 시각이 여러 개면 어느 데스 이야기인지 알 수 없다

        String clock = clocks.iterator().next();
        Death death = deathNear(lane, toSeconds(clock));
        if (death == null) return;
        String at = "'" + clock + "' 데스";

        boolean roam = text.contains("로밍");
        if (roam) {
            acc.context(death.roamInvolved(), at + "는 상대 로밍이 없었다 (실제 유형: " + death.type() + ")");
        }
        if (JUNGLE_CLAIM.matcher(text).find()) {
            acc.context(death.jungleInvolved(), at + "에 상대 정글은 참여하지 않았다 (실제 유형: " + death.type() + ")");
        }
        if (text.contains("라인 교전")) {
            acc.context("라인 교전".equals(death.type()), at + "는 라인 교전이 아니다 (실제 유형: " + death.type() + ")");
        }
        if (text.contains("한타")) {
            acc.context("한타".equals(death.type()), at + "는 한타가 아니다 (실제 유형: " + death.type() + ")");
        }

        List<String> zones = new ArrayList<>();
        for (String z : ZONES) if (text.contains(z)) zones.add(z);
        if (zones.size() == 1) {
            acc.context(zones.get(0).equals(death.zone()),
                    at + " 위치는 '" + zones.get(0) + "'이 아니라 '" + death.zone() + "'이다");
        }

        if (BEFORE_DEATH.matcher(text).find()) {
            Matcher w = WARD_NUMBER.matcher(unit);
            while (w.find()) {
                if (insideSkip(text, w.start(1))) continue;
                int value = Integer.parseInt(w.group(1));
                acc.context(value == death.myWardsLast2Min(),
                        at + " 직전 2분 와드는 " + value + "개가 아니라 " + death.myWardsLast2Min() + "개다");
                blank(unit, w.start(1), w.end(1));
            }
        }
        if (WARD_NONE.matcher(text).find()) {
            acc.context(death.myWardsLast2Min() == 0,
                    at + " 직전 2분에 와드를 " + death.myWardsLast2Min() + "개 설치했다 (와드 없음이 아니다)");
        }
    }

    // ---------- 2) 이름표 붙은 숫자 ----------

    private static void labeledNumbers(LaneReport lane, StringBuilder unit, Acc acc) {
        for (int[] range : sentenceRanges(unit)) {
            String s = unit.substring(range[0], range[1]);

            // 15분까지 설치한 와드 (데스 직전 와드는 위에서 처리)
            if (s.contains("와드") && (s.contains("설치") || s.contains("15분")) && !DEATH_WORDS.matcher(s).find()) {
                Matcher w = WARD_NUMBER.matcher(s);
                while (w.find()) {
                    if (insideSkip(s, w.start(1))) continue;
                    int value = Integer.parseInt(w.group(1));
                    acc.number(value == lane.wardsPlacedBefore15(),
                            "15분까지 설치한 와드는 " + value + "개가 아니라 " + lane.wardsPlacedBefore15() + "개다");
                    blank(unit, range[0] + w.start(1), range[0] + w.end(1));
                }
            }

            // 포탑 방패
            Matcher p = PLATE_NUMBER.matcher(s);
            while (p.find()) {
                int value = Integer.parseInt(p.group(1));
                boolean lost = s.matches("(?s).*(잃|내줬|내주|빼앗|헌납).*");
                boolean taken = s.matches("(?s).*(획득|얻|챙|가져|깎|파괴).*");
                boolean ok = lost && !taken ? value == lane.platesLost()
                        : taken && !lost ? value == lane.platesTaken()
                        : value == lane.platesLost() || value == lane.platesTaken();
                acc.number(ok, "포탑 방패 " + value + "개: 실제로는 획득 " + lane.platesTaken() + "개, 잃음 " + lane.platesLost() + "개");
                blank(unit, range[0] + p.start(1), range[0] + p.end(1));
            }

            // 데스 수
            Matcher d = DEATH_COUNT.matcher(s);
            while (d.find()) {
                int group = d.group(1) != null ? 1 : 2;
                if (insideSkip(s, d.start(group))) continue;
                int value = Integer.parseInt(d.group(group));
                Set<Integer> counts = deathCounts(lane);
                acc.number(counts.contains(value), "데스 " + value + "번: 실제 데스 수(10·15·20분까지)는 " + counts + "이다");
                blank(unit, range[0] + d.start(group), range[0] + d.end(group));
            }

            // 골드·CS 는 쉼표로 나눈 구절 단위로 본다 ("CS 차이는 -3, 와드는 12개" 를 섞지 않도록)
            for (int[] clause : clauseRanges(unit, range)) {
                String c = unit.substring(clause[0], clause[1]);

                if (c.contains("골드")) {
                    Matcher g = GOLD_NUMBER.matcher(c);
                    while (g.find()) {
                        int group = g.group(1) != null ? 1 : 2;
                        if (insideSkip(c, g.start(group))) continue;
                        int value = Math.abs(parse(g.group(group)));
                        acc.number(goldValues(lane).contains(value), "골드 " + g.group(group) + ": 데이터의 골드 격차에 없는 값이다");
                        blank(unit, clause[0] + g.start(group), clause[0] + g.end(group));
                    }
                }

                // CS: 구절에 남은 숫자는 CS 값·격차·격차 변화량 중 하나여야 한다
                if (c.contains("CS")) {
                    String now = unit.substring(clause[0], clause[1]);
                    Matcher n = NUMBER.matcher(now);
                    Set<Integer> allowed = csValues(lane);
                    while (n.find()) {
                        if (insideSkip(now, n.start())) continue;
                        int value = Math.abs(parse(n.group()));
                        acc.number(allowed.contains(value), "CS " + n.group() + ": 데이터의 CS·CS 격차·격차 변화량에 없는 값이다");
                        blank(unit, clause[0] + n.start(), clause[0] + n.end());
                    }
                }
            }
        }
    }

    // ---------- 데이터 값 모음 ----------

    /** "3데스", "라인 교전으로 3번", "정글 갱 사망이 2회" → 시점(10·15·20분, 전체)과 유형별 데스 수 */
    static Set<Integer> deathCounts(LaneReport lane) {
        List<java.util.function.Predicate<Death>> kinds = List.of(
                x -> true,
                x -> "라인 교전".equals(x.type()),
                x -> "한타".equals(x.type()),
                Death::jungleInvolved,
                Death::roamInvolved,
                x -> x.myWardsLast2Min() == 0,
                x -> "상대 진영".equals(x.zone()));
        Set<Integer> counts = new TreeSet<>();
        for (long limit : new long[]{600, 900, 1200, Long.MAX_VALUE}) {
            for (var kind : kinds) {
                counts.add((int) lane.deaths().stream().filter(x -> x.seconds() <= limit).filter(kind).count());
            }
        }
        counts.remove(0);
        counts.add(lane.deaths().size());
        return counts;
    }

    static Set<Integer> goldValues(LaneReport lane) {
        Set<Integer> values = new TreeSet<>();
        lane.checkpoints().forEach(c -> values.add(Math.abs(c.goldDiff())));
        lane.timeline().forEach(p -> values.add(Math.abs(p.goldDiff())));
        return values;
    }

    static Set<Integer> csValues(LaneReport lane) {
        Set<Integer> values = new TreeSet<>();
        lane.checkpoints().forEach(c -> {
            values.add(Math.abs(c.csDiff()));
            values.add(c.myCs());
            values.add(c.opponentCs());
        });
        List<LaneReport.MinutePoint> t = lane.timeline();
        for (int i = 0; i < t.size(); i++) {
            values.add(Math.abs(t.get(i).csDiff()));
            values.add(t.get(i).myCs());
            values.add(t.get(i).opponentCs());
            for (int j = i + 1; j < t.size() && j <= i + 5; j++) { // 5분 안의 격차 변화량 ("8개 더 벌어졌다")
                values.add(Math.abs(t.get(j).csDiff() - t.get(i).csDiff()));
            }
        }
        for (int i = 0; i < lane.checkpoints().size(); i++) {
            for (int j = i + 1; j < lane.checkpoints().size(); j++) {
                values.add(Math.abs(lane.checkpoints().get(j).csDiff() - lane.checkpoints().get(i).csDiff()));
            }
        }
        if (lane.biggestCsSwing() != null) values.add(Math.abs(lane.biggestCsSwing().change()));
        return values;
    }

    static Death deathNear(LaneReport lane, long seconds) {
        Death best = null;
        for (Death d : lane.deaths()) {
            long gap = Math.abs(d.seconds() - seconds);
            if (gap <= TOLERANCE_SECONDS && (best == null || gap < Math.abs(best.seconds() - seconds))) best = d;
        }
        return best;
    }

    // ---------- 문자열 도우미 ----------

    static long toSeconds(String clock) {
        String[] p = clock.split(":");
        return Long.parseLong(p[0]) * 60 + Long.parseLong(p[1]);
    }

    static boolean insideSkip(String text, int index) {
        Matcher m = SKIP.matcher(text);
        while (m.find()) {
            if (index >= m.start() && index < m.end()) return true;
        }
        return false;
    }

    private static int parse(String signed) {
        return Integer.parseInt(signed.replace("−", "-").replace("+", ""));
    }

    private static List<int[]> sentenceRanges(CharSequence text) {
        List<int[]> ranges = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // 소수점(2.3)은 문장 끝이 아니다
            boolean decimal = c == '.' && i > 0 && i + 1 < text.length()
                    && Character.isDigit(text.charAt(i - 1)) && Character.isDigit(text.charAt(i + 1));
            if ((c == '.' || c == '!' || c == '?' || c == '\n') && !decimal) {
                ranges.add(new int[]{start, i + 1});
                start = i + 1;
            }
        }
        if (start < text.length()) ranges.add(new int[]{start, text.length()});
        return ranges;
    }

    /** 문장 안을 쉼표로 나눈 구절 */
    private static List<int[]> clauseRanges(CharSequence text, int[] sentence) {
        List<int[]> out = new ArrayList<>();
        int start = sentence[0];
        for (int i = sentence[0]; i < sentence[1]; i++) {
            if (text.charAt(i) == ',' || text.charAt(i) == '，') {
                out.add(new int[]{start, i});
                start = i + 1;
            }
        }
        out.add(new int[]{start, sentence[1]});
        return out;
    }

    private static List<String> sentences(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (int[] r : sentenceRanges(text)) out.add(text.substring(r[0], r[1]));
        return out;
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null) sb.append(p).append(' ');
        }
        return sb.toString();
    }

    private static void blank(StringBuilder text, int start, int end) {
        for (int i = start; i < end; i++) text.setCharAt(i, ' ');
    }

    private static final class Acc {
        int contextClaims, contextMatched, numberClaims, numberMatched;
        final Set<String> unmatched = new LinkedHashSet<>();

        void context(boolean ok, String problem) {
            contextClaims++;
            if (ok) contextMatched++;
            else unmatched.add(problem);
        }

        void number(boolean ok, String problem) {
            numberClaims++;
            if (ok) numberMatched++;
            else unmatched.add(problem);
        }
    }
}
