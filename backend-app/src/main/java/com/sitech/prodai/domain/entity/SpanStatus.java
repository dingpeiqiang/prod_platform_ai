package com.sitech.prodai.domain.entity;

public enum SpanStatus {
    OK("ok"), ERROR("error"), TIMEOUT("timeout");

    private final String value;

    SpanStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SpanStatus fromValue(String value) {
        for (SpanStatus status : SpanStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
