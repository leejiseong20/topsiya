package com.topsiya.lane;

/** 분석할 수 없는 경기 (내가 없는 경기, 맞라인 상대가 없는 모드 등) */
public class LaneAnalysisException extends RuntimeException {

    public LaneAnalysisException(String message) {
        super(message);
    }
}
