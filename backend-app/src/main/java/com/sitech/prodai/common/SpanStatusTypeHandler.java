package com.sitech.prodai.common;

import com.sitech.prodai.domain.entity.SpanStatus;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * SpanStatus 枚举 TypeHandler —— 对齐原 JPA {@code SpanStatus.SpanStatusConverter}（存 value 字符串）。
 */
@MappedTypes(SpanStatus.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class SpanStatusTypeHandler extends BaseTypeHandler<SpanStatus> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, SpanStatus parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.getValue());
    }

    @Override
    public SpanStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value != null ? SpanStatus.fromValue(value) : null;
    }

    @Override
    public SpanStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value != null ? SpanStatus.fromValue(value) : null;
    }

    @Override
    public SpanStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value != null ? SpanStatus.fromValue(value) : null;
    }
}
