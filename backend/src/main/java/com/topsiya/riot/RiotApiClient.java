package com.topsiya.riot;

import com.topsiya.riot.dto.AccountDto;
import com.topsiya.riot.dto.MatchDto;
import com.topsiya.riot.dto.TimelineDto;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Riot API 호출만 담당한다. (데이터 가공은 MatchService 에서)
 *
 * 문서: https://developer.riotgames.com/apis
 */
@Component
public class RiotApiClient {

    private final RestClient riotRestClient;
    private final RiotProperties properties;

    public RiotApiClient(RestClient riotRestClient, RiotProperties properties) {
        this.riotRestClient = riotRestClient;
        this.properties = properties;
    }

    /** ACCOUNT-V1: Riot ID(닉네임#태그) → PUUID */
    public AccountDto getAccount(String gameName, String tagLine) {
        requireApiKey();
        return riotRestClient.get()
                .uri("/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}", gameName, tagLine)
                .retrieve()
                .body(AccountDto.class);
    }

    /** MATCH-V5: 최근 경기 ID 목록 (최신순) */
    public List<String> getMatchIds(String puuid, int count) {
        requireApiKey();
        List<String> ids = riotRestClient.get()
                .uri("/lol/match/v5/matches/by-puuid/{puuid}/ids?start=0&count={count}", puuid, count)
                .retrieve()
                .body(new ParameterizedTypeReference<List<String>>() {});
        return ids == null ? List.of() : ids;
    }

    /** MATCH-V5: 경기 상세 (참가자 10명의 챔피언, KDA, CS 등) */
    public MatchDto getMatch(String matchId) {
        requireApiKey();
        return riotRestClient.get()
                .uri("/lol/match/v5/matches/{matchId}", matchId)
                .retrieve()
                .body(MatchDto.class);
    }

    /** MATCH-V5: 경기 타임라인 (1분 단위 스냅샷 + 킬·와드·포탑 이벤트) */
    public TimelineDto getTimeline(String matchId) {
        requireApiKey();
        return riotRestClient.get()
                .uri("/lol/match/v5/matches/{matchId}/timeline", matchId)
                .retrieve()
                .body(TimelineDto.class);
    }

    private void requireApiKey() {
        if (!properties.hasApiKey()) {
            throw new RiotApiKeyMissingException();
        }
    }
}
