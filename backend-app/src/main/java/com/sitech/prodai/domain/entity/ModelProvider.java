package com.sitech.prodai.domain.entity;

import java.util.Arrays;
import java.util.List;

public enum ModelProvider {

    OPENAI("openai", "OpenAI Compatible"),
    AZURE("azure", "Azure OpenAI"),
    CUSTOM("custom", "Custom OpenAI Compatible"),
    LOCAL("local", "Local / Mock");

    private final String value;
    private final String label;

    ModelProvider(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static final List<String> STANDARD_PROVIDERS = Arrays.asList(
            OPENAI.getValue(),
            AZURE.getValue(),
            CUSTOM.getValue(),
            LOCAL.getValue()
    );

    public static boolean isStandard(String provider) {
        if (provider == null) {
            return false;
        }
        return STANDARD_PROVIDERS.contains(provider.toLowerCase());
    }

    public static ModelProvider fromValue(String provider) {
        if (provider == null) {
            return CUSTOM;
        }
        String normalized = provider.toLowerCase().trim();
        for (ModelProvider p : values()) {
            if (p.value.equals(normalized)) {
                return p;
            }
        }
        return CUSTOM;
    }

    public static boolean isValid(String provider) {
        return provider != null && !provider.isBlank();
    }
}