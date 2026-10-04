package com.markingsystem.service.llm.spi;

import lombok.Getter;

@Getter
public class LlmException extends RuntimeException {

    public enum Code {
        AUTH_API_KEY_INVALID,
        MODEL_TIMEOUT,
        MODEL_RATE_LIMIT,
        MODEL_UNAVAILABLE,
        TOOL_EXECUTION_FAILED,
        UNKNOWN
    }

    private final Code code;
    private final String provider;

    public LlmException(Code code, String provider, String message) {
        super(message);
        this.code = code;
        this.provider = provider;
    }

    public LlmException(Code code, String provider, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.provider = provider;
    }
}
