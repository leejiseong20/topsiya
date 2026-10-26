package com.topsiya.goal;

public record GoalRequest(GoalMetric metric, Integer target, String text, String position, String sourceMatchId) {
}
