package com.topsiya.riot;

/** 데모 모드인데 저장해 둔 데이터가 없을 때 */
public class DemoDataMissingException extends RuntimeException {

    public DemoDataMissingException() {
        super("데모 모드에서는 미리 저장해 둔 소환사·경기만 볼 수 있어요.");
    }
}
