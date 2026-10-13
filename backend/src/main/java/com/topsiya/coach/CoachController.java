package com.topsiya.coach;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matches")
public class CoachController {

    private final CoachService coachService;

    public CoachController(CoachService coachService) {
        this.coachService = coachService;
    }

    /**
     * AI 코칭 리포트 생성. 호출할 때마다 비용이 들 수 있어서 GET 이 아닌 POST 로 둔다.
     * 예) POST /api/matches/KR_8415925897/coach?puuid=...
     */
    @PostMapping("/{matchId}/coach")
    public CoachingReport coach(@PathVariable String matchId, @RequestParam String puuid) {
        return coachService.coach(matchId, puuid);
    }
}
