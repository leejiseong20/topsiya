package com.topsiya.common;

import com.topsiya.store.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 화면이 알아야 하는 서버 설정 (개발자 화면을 보여줄지, 데모 모드인지) */
@RestController
public class AppInfoController {

    private final AppProperties app;

    public AppInfoController(AppProperties app) {
        this.app = app;
    }

    @GetMapping("/api/app-info")
    public Map<String, Boolean> info() {
        return Map.of("devTools", app.devTools(), "demoMode", app.demoMode());
    }
}
