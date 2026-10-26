package com.topsiya.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParallelTest {

    @Test
    void 순서를_지킨다() {
        List<Integer> result = Parallel.map(List.of(5, 1, 4, 2, 3), n -> {
            sleep(n * 20L); // 늦게 끝나는 것이 있어도
            return n * 10;
        });

        assertThat(result).containsExactly(50, 10, 40, 20, 30);
    }

    @Test
    void 동시에_실행하되_5개를_넘지_않는다() {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();

        Parallel.map(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), n -> {
            peak.accumulateAndGet(running.incrementAndGet(), Math::max);
            sleep(50);
            running.decrementAndGet();
            return n;
        });

        assertThat(peak.get()).isGreaterThan(1).isLessThanOrEqualTo(Parallel.THREADS);
    }

    @Test
    void 실패하면_원래_예외를_그대로_던진다() {
        assertThatThrownBy(() -> Parallel.map(List.of(1, 2), n -> {
            if (n == 2) throw new IllegalStateException("Riot 404");
            return n;
        })).isInstanceOf(IllegalStateException.class).hasMessage("Riot 404");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
