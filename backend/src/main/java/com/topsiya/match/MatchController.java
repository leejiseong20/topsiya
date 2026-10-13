package com.topsiya.match;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    /**
     * 예) GET /api/summoners/Hide on bush/KR1/matches?count=10
     * (프론트에서는 닉네임에 공백·한글이 있어도 encodeURIComponent 로 보낸다)
     */
    @GetMapping("/summoners/{gameName}/{tagLine}/matches")
    public SummonerMatchesResponse getRecentMatches(
            @PathVariable String gameName,
            @PathVariable String tagLine,
            @RequestParam(defaultValue = "10") int count
    ) {
        return matchService.getRecentMatches(gameName.trim(), tagLine.trim(), count);
    }
}
