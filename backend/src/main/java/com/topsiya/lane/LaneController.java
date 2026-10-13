package com.topsiya.lane;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matches")
public class LaneController {

    private final LaneService laneService;

    public LaneController(LaneService laneService) {
        this.laneService = laneService;
    }

    /**
     * 예) GET /api/matches/KR_8415925897/lane?puuid=...
     * puuid 는 경기 목록 응답(SummonerMatchesResponse.puuid)에 들어 있다.
     */
    @GetMapping("/{matchId}/lane")
    public LaneReport getLaneReport(@PathVariable String matchId, @RequestParam String puuid) {
        return laneService.analyze(matchId, puuid);
    }
}
