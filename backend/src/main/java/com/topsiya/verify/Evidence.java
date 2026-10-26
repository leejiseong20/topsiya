package com.topsiya.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * AI 가 근거로 쓸 수 있는 데이터.
 *
 * @param eventSeconds   실제 사건이 있었던 시각(초): 데스, 포탑 파괴
 * @param windows        의미 있는 구간(초): CS 손해가 가장 컸던 3분 등. {시작, 끝}
 * @param knownMinutes   비교 시점(분): 5·10·15분 체크포인트
 * @param numbers        데이터에 있는 모든 숫자(절댓값)와 목록 길이
 */
public record Evidence(
        List<Long> eventSeconds,
        List<long[]> windows,
        Set<Integer> knownMinutes,
        Set<Double> numbers
) {

    /** 객체를 JSON 으로 펼쳐서 안에 있는 숫자를 전부 모은다 (목록 길이 = "데스 3번" 같은 개수도 포함) */
    public static Set<Double> numbersIn(Object data, ObjectMapper objectMapper) {
        Set<Double> numbers = new TreeSet<>();
        collect(objectMapper.valueToTree(data), numbers);
        return numbers;
    }

    private static void collect(JsonNode node, Set<Double> out) {
        if (node.isNumber()) {
            out.add(Math.abs(node.asDouble()));
        } else if (node.isArray()) {
            out.add((double) node.size());
            node.forEach(child -> collect(child, out));
        } else if (node.isObject()) {
            node.forEach(child -> collect(child, out));
        }
    }

    /** "13:20" → 800. 형식이 아니면 null */
    public static Long parseClock(String text) {
        if (text == null) return null;
        String[] parts = text.trim().split(":");
        if (parts.length != 2) return null;
        try {
            return Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
