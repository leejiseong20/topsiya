package com.topsiya.match;

import com.topsiya.riot.RiotApiClient;
import com.topsiya.riot.dto.AccountDto;
import com.topsiya.riot.dto.MatchDto;
import com.topsiya.riot.dto.MatchDto.Participant;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MatchService {

    static final int MAX_COUNT = 20;

    private final RiotApiClient riotApiClient;

    public MatchService(RiotApiClient riotApiClient) {
        this.riotApiClient = riotApiClient;
    }

    /**
     * Riot ID 로 최근 경기 목록을 가져온다.
     * Riot API 호출 횟수 = 1(계정) + 1(경기 ID 목록) + count(경기 상세)
     */
    public SummonerMatchesResponse getRecentMatches(String gameName, String tagLine, int count) {
        int safeCount = Math.max(1, Math.min(count, MAX_COUNT));

        AccountDto account = riotApiClient.getAccount(gameName, tagLine);
        List<String> matchIds = riotApiClient.getMatchIds(account.puuid(), safeCount);

        List<MatchSummary> summaries = new ArrayList<>();
        for (String matchId : matchIds) {
            MatchDto match = riotApiClient.getMatch(matchId);
            MatchSummary summary = toSummary(match, account.puuid());
            if (summary != null) {
                summaries.add(summary);
            }
        }
        return new SummonerMatchesResponse(account.gameName(), account.tagLine(), account.puuid(), summaries);
    }

    /** 경기 상세에서 '나'를 찾아 요약을 만든다. 내가 없으면 null */
    static MatchSummary toSummary(MatchDto match, String myPuuid) {
        if (match == null || match.info() == null || match.info().participants() == null) {
            return null;
        }
        List<Participant> participants = match.info().participants();

        Participant me = participants.stream()
                .filter(p -> myPuuid.equals(p.puuid()))
                .findFirst()
                .orElse(null);
        if (me == null) {
            return null;
        }

        Participant opponent = findLaneOpponent(participants, me);

        return new MatchSummary(
                match.metadata() != null ? match.metadata().matchId() : null,
                match.info().queueId(),
                me.championName(),
                me.teamPosition(),
                me.win(),
                me.kills(),
                me.deaths(),
                me.assists(),
                me.totalMinionsKilled() + me.neutralMinionsKilled(),
                me.champLevel(),
                match.info().gameDuration(),
                match.info().gameEndTimestamp(),
                opponent != null ? opponent.championName() : null
        );
    }

    /** 같은 라인(teamPosition)에 선 상대 팀 플레이어 = 맞라이너 */
    static Participant findLaneOpponent(List<Participant> participants, Participant me) {
        String position = me.teamPosition();
        if (position == null || position.isBlank()) {
            return null;
        }
        return participants.stream()
                .filter(p -> p.teamId() != me.teamId())
                .filter(p -> position.equals(p.teamPosition()))
                .findFirst()
                .orElse(null);
    }
}
