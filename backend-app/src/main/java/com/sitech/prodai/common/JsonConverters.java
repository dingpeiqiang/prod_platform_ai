package com.sitech.prodai.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JPA JSON 转换器集合 —— 对齐 Python SQLAlchemy 的 {@code Column(JSON)} 列。
 *
 * <p>H2 / MySQL 均以 TEXT 存储 JSON 文本，由 Jackson 在 Java 侧完成序列化/反序列化。
 */
public final class JsonConverters {

    private JsonConverters() {
    }

    /** Map&lt;String,Object&gt; &harr; JSON TEXT */
    @Converter
    public static class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        @Override
        public String convertToDatabaseColumn(Map<String, Object> attribute) {
            if (attribute == null) {
                return null;
            }
            try {
                return MAPPER.writeValueAsString(attribute);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize JSON map", e);
            }
        }

        @Override
        public Map<String, Object> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) {
                return new LinkedHashMap<>();
            }
            try {
                return MAPPER.readValue(dbData, new TypeReference<LinkedHashMap<String, Object>>() {
                });
            } catch (Exception e) {
                return Collections.singletonMap("_raw", dbData);
            }
        }
    }

    /** List&lt;Object&gt; &harr; JSON TEXT */
    @Converter
    public static class JsonListConverter implements AttributeConverter<List<Object>, String> {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        @Override
        public String convertToDatabaseColumn(List<Object> attribute) {
            if (attribute == null) {
                return null;
            }
            try {
                return MAPPER.writeValueAsString(attribute);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize JSON list", e);
            }
        }

        @Override
        public List<Object> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) {
                return new ArrayList<>();
            }
            try {
                return MAPPER.readValue(dbData, new TypeReference<ArrayList<Object>>() {
                });
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }
    }
}
