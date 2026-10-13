package com.topsiya.match;

import com.topsiya.riot.RiotApiClient;
import com.topsiya.riot.dto.AccountDto;
import com.topsiya.riot.dto.MatchDto;
import com.topsiya.riot.dto.MatchDto.Participant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchServiceTest {

    private static final String MY_PUUID = "me-puuid";

    @Test
    void 내_기록과_맞라인_상대를_찾는다() {
        MatchDto match = sampleMatch("KR_1", "TOP", "TOP");

        MatchSummary summary = MatchService.toSummary(match, MY_PUUID);

        assertThat(summary).isNotNull();
        assertThat(summary.championName()).isEqualTo("Darius");
        assertThat(summary.position()).isEqualTo("TOP");
        assertThat(summary.win()).isFalse();
        assertThat(summary.kills()).isEqualTo(3);
        assertThat(summary.deaths()).isEqualTo(6);
        assertThat(summary.cs()).isEqualTo(180 + 4);
        assertThat(summary.opponentChampionName()).isEqualTo("Kennen");
    }

    @Test
    void 라인이_없는_모드는_상대가_null() {
        MatchDto match = sampleMatch("KR_2", "", "");

        MatchSummary summary = MatchService.toSummary(match, MY_PUUID);

        assertThat(summary).isNotNull();
        assertThat(summary.opponentChampionName()).isNull();
    }

    @Test
    void 내가_없는_경기는_null() {
        MatchDto match = sampleMatch("KR_3", "TOP", "TOP");

        assertThat(MatchService.toSummary(match, "someone-else")).isNull();
    }

    @Test
    void 경기_개수는_최대_20개로_제한한다() {
        RiotApiClient client = mock(RiotApiClient.class);
        when(client.getAccount("Hide on bush", "KR1"))
                .thenReturn(new AccountDto(MY_PUUID, "Hide on bush", "KR1"));
        when(client.getMatchIds(eq(MY_PUUID), anyInt())).thenReturn(List.of("KR_1"));
        when(client.getMatch("KR_1")).thenReturn(sampleMatch("KR_1", "TOP", "TOP"));

        SummonerMatchesResponse response = new MatchService(client)
                .getRecentMatches("Hide on bush", "KR1", 999);

        verify(client).getMatchIds(MY_PUUID, MatchService.MAX_COUNT);
        assertThat(response.gameName()).isEqualTo("Hide on bush");
        assertThat(response.matches()).hasSize(1);
        assertThat(response.matches().get(0).matchId()).isEqualTo("KR_1");
    }

    /** 나(블루팀 TOP) + 같은 팀 정글 + 상대 정글 + 상대 TOP */
    private static MatchDto sampleMatch(String matchId, String myPosition, String opponentPosition) {
        List<Participant> participants = List.of(
                new Participant(MY_PUUID, "Darius", myPosition, 100, false, 3, 6, 2, 180, 4, 14),
                new Participant("ally-jg", "LeeSin", "JUNGLE", 100, false, 2, 5, 7, 30, 140, 13),
                new Participant("enemy-jg", "Vi", "JUNGLE", 200, true, 6, 2, 8, 25, 150, 15),
                new Participant("enemy-top", "Kennen", opponentPosition, 200, true, 5, 3, 4, 200, 0, 15)
        );
        MatchDto.Info info = new MatchDto.Info(1920, 1_760_000_000_000L, 420, participants);
        MatchDto.Metadata metadata = new MatchDto.Metadata(matchId, List.of());
        return new MatchDto(metadata, info);
    }
}
