package com.topsiya.riot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.topsiya.riot.dto.AccountDto;
import com.topsiya.riot.dto.MatchDto;
import com.topsiya.store.AppProperties;
import com.topsiya.store.FileStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RiotApiClientTest {

    private static final String HOST = "https://asia.api.riotgames.com";
    private static final RiotProperties KEY = new RiotProperties("RGAPI-test", "asia.api.riotgames.com");

    @TempDir
    Path dir;

    private MockRestServiceServer server;

    private RiotApiClient client(boolean demo) {
        RestClient.Builder builder = RestClient.builder().baseUrl(HOST);
        server = MockRestServiceServer.bindTo(builder).build();
        FileStore store = new FileStore(dir, new ObjectMapper());
        return new RiotApiClient(builder.build(), KEY, store, new AppProperties(dir.toString(), demo, false));
    }

    @Test
    void 경기_상세는_한_번_받으면_파일에서_읽는다() {
        RiotApiClient client = client(false);
        server.expect(once(), requestTo(HOST + "/lol/match/v5/matches/KR_1"))
                .andRespond(withSuccess("{\"metadata\":{\"matchId\":\"KR_1\"}}", MediaType.APPLICATION_JSON));

        MatchDto first = client.getMatch("KR_1");
        MatchDto second = client.getMatch("KR_1");

        server.verify(); // Riot 호출은 1번뿐
        assertThat(second.metadata().matchId()).isEqualTo("KR_1");
        assertThat(first).isEqualTo(second);
    }

    @Test
    void 키가_만료돼도_저장된_경기_목록을_쓴다() {
        RiotApiClient client = client(false);
        server.expect(requestTo(HOST + "/lol/match/v5/matches/by-puuid/p/ids?start=0&count=2"))
                .andRespond(withSuccess("[\"KR_2\",\"KR_1\"]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(HOST + "/lol/match/v5/matches/by-puuid/p/ids?start=0&count=2"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        client.getMatchIds("p", 2);
        List<String> ids = client.getMatchIds("p", 2);

        assertThat(ids).containsExactly("KR_2", "KR_1");
    }

    @Test
    void 없는_소환사_404는_저장본으로_덮지_않는다() {
        RiotApiClient client = client(false);
        String url = HOST + "/riot/account/v1/accounts/by-riot-id/Faker/KR1";
        server.expect(requestTo(url))
                .andRespond(withSuccess("{\"puuid\":\"p\",\"gameName\":\"Faker\",\"tagLine\":\"KR1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        client.getAccount("Faker", "KR1");

        assertThatThrownBy(() -> client.getAccount("Faker", "KR1"))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void 데모_모드는_Riot을_부르지_않고_저장본만_쓴다() {
        RiotApiClient online = client(false);
        server.expect(requestTo(HOST + "/riot/account/v1/accounts/by-riot-id/Faker/KR1"))
                .andRespond(withSuccess("{\"puuid\":\"p\",\"gameName\":\"Faker\",\"tagLine\":\"KR1\"}", MediaType.APPLICATION_JSON));
        online.getAccount("Faker", "KR1");
        server.verify();

        RiotApiClient demo = client(true); // 새 서버: 기대 요청 없음 → 호출하면 테스트 실패
        AccountDto account = demo.getAccount("faker", "kr1"); // 대소문자 달라도 같은 소환사

        assertThat(account.puuid()).isEqualTo("p");
        assertThatThrownBy(() -> demo.getMatch("KR_9")).isInstanceOf(DemoDataMissingException.class);
        server.verify();
    }

    @Test
    void 저장본을_써도_되는_오류인지_구분한다() {
        assertThat(RiotApiClient.canUseSavedCopy(new RiotApiKeyMissingException())).isTrue();
        assertThat(RiotApiClient.canUseSavedCopy(
                new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS))).isTrue();
        assertThat(RiotApiClient.canUseSavedCopy(
                new HttpClientErrorException(HttpStatus.NOT_FOUND))).isFalse();
        assertThat(RiotApiClient.canUseSavedCopy(new IllegalStateException())).isFalse();
    }
}
