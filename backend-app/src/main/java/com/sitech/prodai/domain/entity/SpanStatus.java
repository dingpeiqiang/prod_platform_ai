package com.sitech.prodai.domain.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

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

    @Converter(autoApply = true)
    public static class SpanStatusConverter implements AttributeConverter<SpanStatus, String> {
        @Override
        public String convertToDatabaseColumn(SpanStatus status) {
            return status != null ? status.getValue() : null;
        }

        @Override
        public SpanStatus convertToEntityAttribute(String value) {
            return value != null ? SpanStatus.fromValue(value) : null;
        }
    }
}