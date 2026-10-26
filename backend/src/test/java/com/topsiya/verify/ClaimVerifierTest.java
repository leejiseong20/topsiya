package com.topsiya.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimVerifierTest {

    /** 데스 2:55, 13:20, 18:28 / 손해 구간 12~15분 / 체크포인트 5·10·15분 / 숫자 0, 3, 14, 18, 2.33 */
    private static final Evidence EVIDENCE = new Evidence(
            List.of(175L, 800L, 1108L),
            List.of(new long[]{720, 900}),
            Set.of(5, 10, 15),
            Set.of(0.0, 3.0, 14.0, 18.0, 2.3333)
    );

    @Test
    void 실제_데스_시각은_통과_없는_시각은_걸린다() {
        Verification v = ClaimVerifier.verify(List.of("13:20 에 갱을 당했고 14:10 에도 죽었다"), EVIDENCE);

        assertThat(v.timeClaims()).isEqualTo(2);
        assertThat(v.timeMatched()).isEqualTo(1);
        assertThat(v.unmatched()).singleElement().asString().contains("14:10");
    }

    @Test
    void 삼십초_오차는_허용한다() {
        assertThat(ClaimVerifier.verify(List.of("13:40"), EVIDENCE).allMatched()).isTrue();
        assertThat(ClaimVerifier.verify(List.of("13:51"), EVIDENCE).allMatched()).isFalse();
    }

    @Test
    void 구간은_사건이나_손해구간과_겹치면_통과() {
        assertThat(ClaimVerifier.verify(List.of("17~20분에 무너졌다"), EVIDENCE).allMatched()).isTrue();  // 18:28 데스
        assertThat(ClaimVerifier.verify(List.of("12~15분 CS 손해"), EVIDENCE).allMatched()).isTrue();    // 손해 구간
        assertThat(ClaimVerifier.verify(List.of("5~10분은 무난했다"), EVIDENCE).allMatched()).isTrue();   // 체크포인트 사이
        assertThat(ClaimVerifier.verify(List.of("6~8분에 밀렸다"), EVIDENCE).allMatched()).isFalse();
    }

    @Test
    void 숫자는_데이터에_있어야_한다() {
        Verification v = ClaimVerifier.verify(List.of("CS 격차가 14개 벌어졌고 와드는 0개, 데스는 37번"), EVIDENCE);

        assertThat(v.numberClaims()).isEqualTo(3);
        assertThat(v.numberMatched()).isEqualTo(2);
        assertThat(v.unmatched()).singleElement().asString().contains("37");
    }

    @Test
    void 소수는_반올림_오차를_허용한다() {
        assertThat(ClaimVerifier.verify(List.of("평균 2.3번 죽었다"), EVIDENCE).allMatched()).isTrue();
    }

    @Test
    void 기준을_말하는_N분이나_1차_포탑은_검사하지_않는다() {
        Verification v = ClaimVerifier.verify(List.of("15분 전, 죽기 전 2분 동안 와드가 없었고 1차 포탑을 잃었다"), EVIDENCE);

        assertThat(v.timeClaims()).isZero();
        assertThat(v.numberClaims()).isZero();
    }

    @Test
    void 같은_표현은_한_번만_센다() {
        Verification v = ClaimVerifier.verify(List.of("13:20 갱", "13:20 에 와드 0개"), EVIDENCE);

        assertThat(v.timeClaims()).isEqualTo(1);
        assertThat(v.numberClaims()).isEqualTo(1);
    }

    @Test
    void 다시_쓰기_요청문에_틀린_내용이_들어간다() {
        Verification v = ClaimVerifier.verify(List.of("14:10 에 죽었다"), EVIDENCE);

        assertThat(v.feedback()).contains("14:10").contains("다시 작성");
    }

    @Test
    void 측정_통계는_첫_답과_최종_답을_따로_센다() {
        Verification bad = new Verification(2, 1, 2, 2, List.of("x"));
        Verification good = new Verification(2, 2, 2, 2, List.of());

        EvalSummary s = EvalSummary.of(List.of(new Verification[]{good, bad}, new Verification[]{good, null}));

        assertThat(s.reports()).isEqualTo(2);
        assertThat(s.rewritten()).isEqualTo(1);
        assertThat(s.firstDraft().timeRate()).isEqualTo(75.0);
        assertThat(s.afterCheck().timeRate()).isEqualTo(100.0);
        assertThat(s.fullyMatched()).isEqualTo(2);
    }

    @Test
    void 측정_통계_JSON에_비율이_들어간다() throws Exception {
        Verification good = new Verification(2, 2, 4, 3, List.of("숫자 9: 데이터에 없는 숫자다"));

        String json = new ObjectMapper().writeValueAsString(EvalSummary.of(List.<Verification[]>of(new Verification[]{good, null})));

        assertThat(json).contains("\"timeRate\":100.0").contains("\"numberRate\":75.0").contains("\"overallRate\":83.3");
    }
}
