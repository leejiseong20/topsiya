package com.topsiya.goal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 도전 중인 목표: 조회(지켰는지 판정 포함) · 정하기 · 그만하기 */
@RestController
@RequestMapping("/api/goals/{puuid}")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping
    public ResponseEntity<GoalProgress> get(@PathVariable String puuid) {
        return goalService.progress(puuid)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    public GoalProgress set(@PathVariable String puuid, @RequestBody GoalRequest request) {
        return goalService.set(puuid, request);
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(@PathVariable String puuid) {
        goalService.clear(puuid);
        return ResponseEntity.noContent().build();
    }
}
