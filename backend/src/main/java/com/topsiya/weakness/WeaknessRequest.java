package com.topsiya.weakness;

import java.util.List;

/**
 * @param puuid    경기 목록 응답에 들어 있는 내 puuid
 * @param position 분석할 포지션 (TOP, JUNGLE, MIDDLE, BOTTOM, UTILITY)
 * @param matchIds 그 포지션으로 플레이한 경기 ID 들 (2~10개)
 */
public record WeaknessRequest(String puuid, String position, List<String> matchIds) {
}
