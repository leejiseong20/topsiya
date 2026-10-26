package com.topsiya.coach;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.topsiya.lane.LaneReport;
import com.topsiya.verify.ClaimVerifier;
import com.topsiya.verify.Evidence;
import com.topsiya.verify.Verification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 한 경기 코칭을 검증할 때 쓰는 근거 데이터 */
final class CoachEvidence {

    private CoachEvidence() {
    }

    /** 엄격한 검사(시각+내용, 이름표 붙은 숫자) → 남은 시각·숫자는 느슨한 검사 */
    static Verification verify(LaneReport lane, CoachingReport report, ObjectMapper objectMapper) {
        CoachContextCheck.Result strict = CoachContextCheck.check(lane, report);
        Verification loose = ClaimVerifier.verify(strict.rest(), of(lane, objectMapper));
        return strict.verification().plus(loose);
    }

    /** 예전 느슨한 검사만 (검사기 시험에서 비교용) */
    static Verification verifyLoose(LaneReport lane, CoachingReport report, ObjectMapper objectMapper) {
        return ClaimVerifier.verify(texts(report), of(lane, objectMapper));
    }

    static Evidence of(LaneReport lane, ObjectMapper objectMapper) {
        List<Long> events = new ArrayList<>();
        lane.deaths().forEach(d -> events.add(d.seconds()));
        addClock(events, lane.myOuterTowerLostAt());
        addClock(events, lane.enemyOuterTowerDestroyedAt());

        List<long[]> windows = new ArrayList<>();
        if (lane.biggestCsSwing() != null) {
            windows.add(new long[]{lane.biggestCsSwing().fromMinute() * 60L, lane.biggestCsSwing().toMinute() * 60L});
        }

        Set<Integer> minutes = new TreeSet<>();
        lane.checkpoints().forEach(c -> minutes.add(c.minute()));

        return new Evidence(events, windows, minutes, Evidence.numbersIn(lane, objectMapper));
    }

    /** 사용자에게 보이는 글 전부 */
    static List<String> texts(CoachingReport report) {
        List<String> texts = new ArrayList<>();
        texts.add(report.summary());
        texts.add(report.nextGameTip());
        if (report.mistakes() != null) {
            report.mistakes().forEach(m -> {
                texts.add(m.time());
                texts.add(m.what());
                texts.add(m.why());
                texts.add(m.fix());
            });
        }
        if (report.goodPoints() != null) {
            texts.addAll(report.goodPoints());
        }
        return texts;
    }

    private static void addClock(List<Long> events, String clock) {
        Long seconds = Evidence.parseClock(clock);
        if (seconds != null) events.add(seconds);
    }
}
