package com.topsiya.verify;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 가 쓴 문장에서 시각·숫자를 뽑아 데이터와 대조한다. (AI 가 지어낸 내용 걸러내기)
 *
 *  1) "13:20" 같은 시각   → 앞뒤 30초 안에 실제 사건(데스·포탑)이 있어야 한다
 *  2) "12~15분" 같은 구간 → 그 안에 사건이 있거나, 손해 구간과 겹치거나, 체크포인트(5·10·15분) 사이여야 한다
 *  3) 그 밖의 숫자        → 데이터에 같은 숫자가 있어야 한다 (소수는 반올림 오차 허용)
 *  "15분 전", "2분 동안" 같은 단독 "N분"은 기준을 말하는 표현이라 검사하지 않는다.
 */
public final class ClaimVerifier {

    static final long CLOCK_TOLERANCE_SECONDS = 30;

    private static final Pattern CLOCK = Pattern.compile("(?<!\\d)(\\d{1,2}):(\\d{2})(?!\\d)");
    private static final Pattern MINUTE_RANGE = Pattern.compile("(\\d{1,2})\\s*(?:~|-|–|부터)\\s*(\\d{1,2})\\s*분");
    private static final Pattern MINUTE = Pattern.compile("\\d{1,2}\\s*분");
    /** "1차 포탑", "1대1", "2명", "3판" 처럼 셈이 아닌 표현은 숫자 검사에서 뺀다 */
    private static final Pattern NUMBER = Pattern.compile("(?<![\\d.])[+\\-−]?(\\d+(?:\\.\\d+)?)(?![\\d.]|\\s*(?:차|대|명|판|경기|분|라인))");

    private ClaimVerifier() {
    }

    public static Verification verify(List<String> texts, Evidence evidence) {
        int timeClaims = 0, timeMatched = 0, numberClaims = 0, numberMatched = 0;
        Set<String> unmatched = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>(); // 같은 표현이 여러 번 나오면 한 번만 센다

        for (String raw : texts) {
            if (raw == null || raw.isBlank()) continue;
            StringBuilder text = new StringBuilder(raw);

            // 1) 시각
            Matcher clock = CLOCK.matcher(text);
            while (clock.find()) {
                String claim = clock.group();
                long seconds = Long.parseLong(clock.group(1)) * 60 + Long.parseLong(clock.group(2));
                if (seen.add("t:" + claim)) {
                    timeClaims++;
                    if (clockMatches(seconds, evidence)) timeMatched++;
                    else unmatched.add("'" + claim + "': 이 시각 앞뒤 30초 안에 데이터에 기록된 사건(데스·포탑)이 없다");
                }
                blank(text, clock.start(), clock.end());
            }

            // 2) 분 구간
            Matcher range = MINUTE_RANGE.matcher(text);
            while (range.find()) {
                String claim = range.group(1) + "~" + range.group(2) + "분";
                long from = Long.parseLong(range.group(1)) * 60;
                long to = Long.parseLong(range.group(2)) * 60 + 59;
                if (seen.add("t:" + claim)) {
                    timeClaims++;
                    if (rangeMatches(from, to, evidence)) timeMatched++;
                    else unmatched.add("'" + claim + "': 이 구간에 데이터상 사건이나 CS 손해 구간이 없다");
                }
                blank(text, range.start(), range.end());
            }

            // 3) 단독 "N분" 은 기준 표현 → 지운다
            Matcher minute = MINUTE.matcher(text);
            while (minute.find()) {
                blank(text, minute.start(), minute.end());
            }

            // 4) 숫자
            Matcher number = NUMBER.matcher(text);
            while (number.find()) {
                double value = Double.parseDouble(number.group(1));
                String key = "n:" + number.group(1);
                if (!seen.add(key)) continue;
                numberClaims++;
                if (numberMatches(value, evidence)) numberMatched++;
                else unmatched.add("숫자 " + number.group(1) + ": 데이터에 없는 숫자다");
            }
        }
        return new Verification(timeClaims, timeMatched, numberClaims, numberMatched, new ArrayList<>(unmatched));
    }

    static boolean clockMatches(long seconds, Evidence e) {
        for (long event : e.eventSeconds()) {
            if (Math.abs(event - seconds) <= CLOCK_TOLERANCE_SECONDS) return true;
        }
        // "10:00" 처럼 딱 떨어지는 분은 체크포인트를 가리킨 것으로 본다
        return seconds % 60 == 0 && e.knownMinutes().contains((int) (seconds / 60));
    }

    static boolean rangeMatches(long from, long to, Evidence e) {
        for (long event : e.eventSeconds()) {
            if (event >= from && event <= to) return true;
        }
        for (long[] w : e.windows()) {
            if (w[0] <= to && from <= w[1]) return true;
        }
        int fromMin = (int) (from / 60);
        int toMin = (int) (to / 60);
        return e.knownMinutes().contains(fromMin) && e.knownMinutes().contains(toMin);
    }

    static boolean numberMatches(double value, Evidence e) {
        for (double n : e.numbers()) {
            if (Math.abs(n - value) <= 0.051) return true;
        }
        return false;
    }

    private static void blank(StringBuilder text, int start, int end) {
        for (int i = start; i < end; i++) {
            text.setCharAt(i, ' ');
        }
    }
}
