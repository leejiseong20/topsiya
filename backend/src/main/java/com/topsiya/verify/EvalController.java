package com.topsiya.verify;

import com.topsiya.store.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 정확도 측정 (개발자용).
 * topsiya.dev-tools 가 꺼져 있으면 없는 주소처럼 404 를 돌려준다.
 * (일반 사용자가 측정 버튼으로 AI 비용을 쓰지 못하게)
 */
@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final EvalService evalService;
    private final AppProperties app;

    public EvalController(EvalService evalService, AppProperties app) {
        this.evalService = evalService;
        this.app = app;
    }

    /** 지금까지 저장된 AI 답의 검증 통계 */
    @GetMapping
    public Map<String, EvalSummary> summary() {
        requireDevTools();
        return evalService.summary();
    }

    /** 검사기 시험: 틀린 내용을 일부러 심어서 몇 개를 잡는지 (AI 호출 없음) */
    @GetMapping("/probe")
    public ProbeSummary probe() {
        requireDevTools();
        return evalService.probe();
    }

    /** 최근 경기들에 AI 코칭을 한꺼번에 돌린 뒤 통계를 돌려준다 (AI 호출 비용 발생) */
    @PostMapping("/run")
    public Map<String, Object> run(@RequestParam String puuid, @RequestParam(defaultValue = "10") int count) {
        requireDevTools();
        int done = evalService.run(puuid, count);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("analyzedGames", done);
        result.putAll(evalService.summary());
        return result;
    }

    private void requireDevTools() {
        if (!app.devTools()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
