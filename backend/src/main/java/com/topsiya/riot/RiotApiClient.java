package com.topsiya.riot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.topsiya.riot.dto.AccountDto;
import com.topsiya.riot.dto.MatchDto;
import com.topsiya.riot.dto.TimelineDto;
import com.topsiya.store.AppProperties;
import com.topsiya.store.FileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Riot API 호출만 담당한다. (데이터 가공은 MatchService 에서)
 * 문서: https://developer.riotgames.com/apis
 *
 * 받은 응답은 파일로 저장해 둔다.
 *  - 경기 상세·타임라인: 끝난 경기는 바뀌지 않으니, 저장본이 있으면 Riot 을 부르지 않는다.
 *  - 계정·최근 경기 목록: 바뀔 수 있으니 매번 새로 받되, 실패하면(인터넷·키 만료·서버 오류) 저장본을 쓴다.
 *  - 데모 모드: Riot 을 아예 부르지 않고 저장본만 쓴다.
 */
@Component
public class RiotApiClient {

    private static final Logger log = LoggerFactory.getLogger(RiotApiClient.class);

    private final RestClient riotRestClient;
    private final RiotProperties properties;
    private final FileStore store;
    private final AppProperties app;

    public RiotApiClient(@Qualifier("riotRestClient") RestClient riotRestClient, RiotProperties properties,
                         FileStore store, AppProperties app) {
        this.riotRestClient = riotRestClient;
        this.properties = properties;
        this.store = store;
        this.app = app;
    }

    /** ACCOUNT-V1: Riot ID(닉네임#태그) → PUUID */
    public AccountDto getAccount(String gameName, String tagLine) {
        String key = (gameName + "#" + tagLine).toLowerCase(Locale.ROOT);
        return fresh("account", key, new TypeReference<AccountDto>() {}, () -> riotRestClient.get()
                .uri("/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}", gameName, tagLine)
                .retrieve()
                .body(AccountDto.class));
    }

    /** MATCH-V5: 최근 경기 ID 목록 (최신순) */
    public List<String> getMatchIds(String puuid, int count) {
        List<String> ids = fresh("match-ids", puuid, new TypeReference<List<String>>() {}, () -> riotRestClient.get()
                .uri("/lol/match/v5/matches/by-puuid/{puuid}/ids?start=0&count={count}", puuid, count)
                .retrieve()
                .body(new ParameterizedTypeReference<List<String>>() {}));
        if (ids == null) {
            return List.of();
        }
        return ids.size() > count ? ids.subList(0, count) : ids;
    }

    /** MATCH-V5: 경기 상세 (참가자 10명의 챔피언, KDA, CS 등) */
    public MatchDto getMatch(String matchId) {
        return once("match", matchId, MatchDto.class, () -> riotRestClient.get()
                .uri("/lol/match/v5/matches/{matchId}", matchId)
                .retrieve()
                .body(MatchDto.class));
    }

    /** MATCH-V5: 경기 타임라인 (1분 단위 스냅샷 + 킬·와드·포탑 이벤트) */
    public TimelineDto getTimeline(String matchId) {
        return once("timeline", matchId, TimelineDto.class, () -> riotRestClient.get()
                .uri("/lol/match/v5/matches/{matchId}/timeline", matchId)
                .retrieve()
                .body(TimelineDto.class));
    }

    /** 바뀌지 않는 데이터: 저장본 → 없으면 Riot 호출 후 저장 */
    private <T> T once(String category, String key, Class<T> type, Supplier<T> call) {
        Optional<T> saved = store.read(category, key, type);
        if (saved.isPresent()) {
            return saved.get();
        }
        if (app.demoMode()) {
            throw new DemoDataMissingException();
        }
        requireApiKey();
        T value = call.get();
        if (value != null) {
            store.write(category, key, value);
        }
        return value;
    }

    /** 바뀔 수 있는 데이터: Riot 호출 후 저장 → 실패하면 저장본 */
    private <T> T fresh(String category, String key, TypeReference<T> type, Supplier<T> call) {
        if (app.demoMode()) {
            return store.read(category, key, type).orElseThrow(DemoDataMissingException::new);
        }
        try {
            requireApiKey();
            T value = call.get();
            if (value != null) {
                store.write(category, key, value);
            }
            return value;
        } catch (RuntimeException e) {
            if (!canUseSavedCopy(e)) {
                throw e;
            }
            Optional<T> saved = store.read(category, key, type);
            if (saved.isEmpty()) {
                throw e;
            }
            log.warn("Riot API 호출 실패 → 저장된 데이터 사용 ({}): {}", category, e.getMessage());
            return saved.get();
        }
    }

    /** 404(없는 소환사)·400(잘못된 입력)은 저장본으로 덮으면 안 된다. 나머지 장애는 저장본으로 버틴다. */
    static boolean canUseSavedCopy(RuntimeException e) {
        if (e instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            return status != 404 && status != 400;
        }
        return e instanceof ResourceAccessException || e instanceof RiotApiKeyMissingException;
    }

    private void requireApiKey() {
        if (!properties.hasApiKey()) {
            throw new RiotApiKeyMissingException();
        }
    }
}
