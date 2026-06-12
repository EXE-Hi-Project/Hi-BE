package com.hi.api.exception;

public class AiServiceBusyException extends RuntimeException {

    public AiServiceBusyException() {
        super("Hi AI đang có nhiều yêu cầu. Vui lòng thử lại sau ít phút.");
    }
}
