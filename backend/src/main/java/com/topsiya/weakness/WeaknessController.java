package com.topsiya.weakness;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WeaknessController {

    private final WeaknessService weaknessService;

    public WeaknessController(WeaknessService weaknessService) {
        this.weaknessService = weaknessService;
    }

    /**
     * 같은 포지션의 여러 경기를 묶어 반복되는 약점을 분석한다.
     * 예) POST /api/weaknesses
     *     {"puuid":"...","position":"UTILITY","matchIds":["KR_1","KR_2","KR_3"]}
     */
    @PostMapping("/api/weaknesses")
    public WeaknessReport analyze(@RequestBody WeaknessRequest request) {
        return weaknessService.analyze(request);
    }
}
