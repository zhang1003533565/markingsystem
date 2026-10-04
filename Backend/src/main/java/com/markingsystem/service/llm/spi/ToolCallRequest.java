package com.markingsystem.service.llm.spi;

import lombok.Data;

@Data
public class ToolCallRequest {
    private String id;
    private String type = "function";
    private Function function;

    @Data
    public static class Function {
        private String name;
        private String arguments;
    }
}
