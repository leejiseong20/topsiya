package com.topsiya.weakness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.topsiya.verify.ClaimVerifier;
import com.topsiya.verify.Evidence;
import com.topsiya.verify.Verification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 여러 경기 통계 해석을 검증할 때 쓰는 근거 데이터 */
final class WeaknessEvidence {

    private WeaknessEvidence() {
    }

    private static final Pattern FREQUENCY = Pattern.compile("(\\d+)\\s*경기\\s*중\\s*(\\d+)\\s*경기");

    static Verification verify(WeaknessStats stats, WeaknessReport.AiPart ai, ObjectMapper objectMapper) {
        Verification base = ClaimVerifier.verify(texts(ai), of(stats, objectMapper));

        // "7경기 중 5경기" → 앞 숫자는 분석한 경기 수와 같고, 뒤 숫자는 그보다 클 수 없다
        int claims = 0;
        int matched = 0;
        List<String> unmatched = new ArrayList<>(base.unmatched());
        for (String text : texts(ai)) {
            if (text == null) continue;
            Matcher m = FREQUENCY.matcher(text);
            while (m.find()) {
                claims++;
                int total = Integer.parseInt(m.group(1));
                int hit = Integer.parseInt(m.group(2));
                if (total == stats.games() && hit <= total) {
                    matched++;
                } else {
                    unmatched.add("'" + m.group() + "': 분석한 경기는 " + stats.games() + "경기다");
                }
            }
        }
        return new Verification(base.timeClaims(), base.timeMatched(),
                base.numberClaims() + claims, base.numberMatched() + matched, unmatched);
    }

    static Evidence of(WeaknessStats stats, ObjectMapper objectMapper) {
        // "10~15분에 자주 무너진다" → 손해 구간 분포에 실제로 있는 5분 구간만 근거로 인정
        List<long[]> windows = new ArrayList<>();
        WeaknessStats.SwingBuckets b = stats.swingStarts();
        if (b != null) {
            int[] counts = {b.from0to5(), b.from5to10(), b.from10to15(), b.from15to20()};
            for (int i = 0; i < counts.length; i++) {
                if (counts[i] > 0) windows.add(new long[]{i * 300L, i * 300L + 299});
            }
        }
        return new Evidence(List.of(), windows, Set.of(5, 10, 15, 20), Evidence.numbersIn(stats, objectMapper));
    }

    static List<String> texts(WeaknessReport.AiPart ai) {
        List<String> texts = new ArrayList<>();
        texts.add(ai.summary());
        texts.add(ai.focus());
        if (ai.weaknesses() != null) {
            ai.weaknesses().forEach(w -> {
                texts.add(w.title());
                texts.add(w.evidence());
                texts.add(w.frequency());
                texts.add(w.fix());
            });
        }
        if (ai.strengths() != null) {
            texts.addAll(ai.strengths());
        }
        return texts;
    }
}
