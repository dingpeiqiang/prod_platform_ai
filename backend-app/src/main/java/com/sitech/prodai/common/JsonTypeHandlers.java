package com.sitech.prodai.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MyBatis JSON TypeHandler 集合 —— 对齐原 JPA {@code JsonConverters}（Column(JSON) 列）。
 *
 * <p>MySQL 以 TEXT 存储 JSON 文本，由 Jackson 在 Java 侧完成序列化/反序列化。
 * 实体字段通过 {@code @TableField(typeHandler = ...)} 指定。
 */
public final class JsonTypeHandlers {

    private JsonTypeHandlers() {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Map&lt;String,Object&gt; &harr; JSON TEXT */
    @MappedTypes(Map.class)
    @MappedJdbcTypes(JdbcType.VARCHAR)
    public static class JsonMapTypeHandler extends BaseTypeHandler<Map<String, Object>> {

        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType)
                throws SQLException {
            try {
                ps.setString(i, MAPPER.writeValueAsString(parameter));
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to serialize JSON map", e);
            }
        }

        @Override
        public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
            return parseMap(rs.getString(columnName));
        }

        @Override
        public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            return parseMap(rs.getString(columnIndex));
        }

        @Override
        public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            return parseMap(cs.getString(columnIndex));
        }

        private Map<String, Object> parseMap(String dbData) {
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
    @MappedTypes(List.class)
    @MappedJdbcTypes(JdbcType.VARCHAR)
    public static class JsonListTypeHandler extends BaseTypeHandler<List<Object>> {

        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, List<Object> parameter, JdbcType jdbcType)
                throws SQLException {
            try {
                ps.setString(i, MAPPER.writeValueAsString(parameter));
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to serialize JSON list", e);
            }
        }

        @Override
        public List<Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
            return parseList(rs.getString(columnName));
        }

        @Override
        public List<Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            return parseList(rs.getString(columnIndex));
        }

        @Override
        public List<Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            return parseList(cs.getString(columnIndex));
        }

        private List<Object> parseList(String dbData) {
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
