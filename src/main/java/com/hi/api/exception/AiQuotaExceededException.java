package com.hi.api.exception;

import lombok.Getter;

import java.time.Instant;

@Getter
public class AiQuotaExceededException extends RuntimeException {

    private final int limit;
    private final int used;
    private final Instant resetsAt;

    public AiQuotaExceededException(int limit, int used, Instant resetsAt) {
        super("Bạn đã dùng hết lượt Hi AI hôm nay. Hạn mức sẽ được làm mới vào ngày mai.");
        this.limit = limit;
        this.used = used;
        this.resetsAt = resetsAt;
    }
}
