package com.topsiya.common;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 외부 API 를 여러 번 부를 때 동시에 처리한다 (경기 10개를 하나씩 → 5개씩 동시에).
 * Riot 개인 키 한도(초당 20회)를 넘지 않도록 동시 실행은 5개로 제한한다.
 */
public final class Parallel {

    static final int THREADS = 5;

    private static final AtomicInteger COUNT = new AtomicInteger();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(THREADS, r -> {
        Thread t = new Thread(r, "topsiya-io-" + COUNT.incrementAndGet());
        t.setDaemon(true); // 서버 종료를 막지 않게
        return t;
    });

    private Parallel() {
    }

    /** 순서를 지킨 채 동시에 변환한다. 하나라도 실패하면 그 예외를 그대로 던진다 */
    public static <T, R> List<R> map(List<T> items, Function<T, R> task) {
        List<CompletableFuture<R>> futures = items.stream()
                .map(item -> CompletableFuture.supplyAsync(() -> task.apply(item), POOL))
                .toList();
        try {
            return futures.stream().map(CompletableFuture::join).toList();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime; // 원래 예외(Riot 404 등)를 그대로 → GlobalExceptionHandler 가 처리
            }
            throw e;
        }
    }
}
