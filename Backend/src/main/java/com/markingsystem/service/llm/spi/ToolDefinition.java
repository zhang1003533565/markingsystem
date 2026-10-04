package com.markingsystem.service.llm.spi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {
    private String type = "function";
    private Function function;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Function {
        private String name;
        private String description;
        private Map<String, Object> parameters;
    }

    public ToolDefinition(String name, String description, Map<String, Object> parameters) {
        this.type = "function";
        this.function = new Function(name, description, parameters);
    }
}
