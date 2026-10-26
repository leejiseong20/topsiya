package com.topsiya.goal;

import com.topsiya.lane.LaneReport;
import com.topsiya.lane.LaneReport.Death;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * "다음 판엔 이것만"을 숫자로 확인할 수 있게 만든 지표.
 * AI 는 이 중 하나와 목표값을 고르고, 다음 경기에서 서버가 라인전 데이터로 지켰는지 판정한다.
 * (판정은 AI 가 아니라 코드가 한다 → 매번 같은 기준)
 */
public enum GoalMetric {

    DEATHS_BEFORE_15("15분 전 데스", "번", false,
            r -> countEarlyDeaths(r, d -> true)),
    WARDLESS_DEATHS_BEFORE_15("15분 전 와드 없이 죽은 횟수", "번", false,
            r -> countEarlyDeaths(r, d -> d.myWardsLast2Min() == 0)),
    JUNGLE_GANK_DEATHS_BEFORE_15("15분 전 상대 정글에게 잡힌 횟수", "번", false,
            r -> countEarlyDeaths(r, Death::jungleInvolved)),
    ROAM_DEATHS_BEFORE_15("15분 전 상대 로밍에 잡힌 횟수", "번", false,
            r -> countEarlyDeaths(r, Death::roamInvolved)),
    ENEMY_ZONE_DEATHS_BEFORE_15("15분 전 상대 진영에서 죽은 횟수", "번", false,
            r -> countEarlyDeaths(r, d -> "상대 진영".equals(d.zone()))),
    SOLO_DEATHS_BEFORE_15("15분 전 라인 상대에게 1:1로 죽은 횟수", "번", false,
            LaneReport::soloDeaths),
    CS_DIFF_AT_10("10분 CS 차이", "개", true,
            r -> csDiffAt(r, 10)),
    CS_DIFF_AT_15("15분 CS 차이", "개", true,
            r -> csDiffAt(r, 15)),
    WARDS_BEFORE_15("15분까지 설치한 와드", "개", true,
            LaneReport::wardsPlacedBefore15);

    static final long LANE_PHASE_END_SECONDS = 15 * 60;

    private final String label;
    private final String unit;
    /** true: 목표값 이상이면 성공 (CS·와드) / false: 목표값 이하면 성공 (데스) */
    private final boolean higherIsBetter;
    private final Function<LaneReport, Integer> measure;

    GoalMetric(String label, String unit, boolean higherIsBetter, Function<LaneReport, Integer> measure) {
        this.label = label;
        this.unit = unit;
        this.higherIsBetter = higherIsBetter;
        this.measure = measure;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }

    public boolean higherIsBetter() {
        return higherIsBetter;
    }

    /** 이 경기의 값. 경기가 너무 짧아 잴 수 없으면 null */
    public Integer measure(LaneReport report) {
        return measure.apply(report);
    }

    public boolean achieved(int value, int target) {
        return higherIsBetter ? value >= target : value <= target;
    }

    /** 예: "15분 전 와드 없이 죽은 횟수 0번 이하", "10분 CS 차이 +5개 이상" */
    public String describe(int target) {
        String number = unit.equals("개") && label.contains("차이") && target > 0 ? "+" + target : String.valueOf(target);
        return label + " " + number + unit + (higherIsBetter ? " 이상" : " 이하");
    }

    /** AI 지시문에 넣을 지표 목록 */
    public static String promptList() {
        return Arrays.stream(values())
                .map(m -> "· " + m.name() + ": " + m.label + " (" + (m.higherIsBetter ? "목표값 이상이면 성공" : "목표값 이하면 성공") + ")")
                .collect(Collectors.joining("\n"));
    }

    private static Integer countEarlyDeaths(LaneReport r, Predicate<Death> filter) {
        if (r.gameDurationSeconds() < LANE_PHASE_END_SECONDS) {
            return null; // 15분 전에 끝난 경기(항복 등)는 판정하지 않는다
        }
        return (int) r.deaths().stream()
                .filter(d -> d.seconds() <= LANE_PHASE_END_SECONDS)
                .filter(filter)
                .count();
    }

    private static Integer csDiffAt(LaneReport r, int minute) {
        return r.checkpoints().stream()
                .filter(c -> c.minute() == minute)
                .map(LaneReport.Checkpoint::csDiff)
                .findFirst()
                .orElse(null);
    }
}
