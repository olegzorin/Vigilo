package dev.olegz.vf.registry.dao.handlers;

import java.sql.*;

import dev.olegz.vf.common.Datetime;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public class DatetimeTypeHandler extends BaseTypeHandler<Datetime> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Datetime parameter, JdbcType jdbcType)
        throws SQLException {
        ps.setTimestamp(i, new Timestamp(parameter.getTime()));
    }

    @Override
    public Datetime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toDatetime(rs.getTimestamp(columnName));
    }

    @Override
    public Datetime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toDatetime(rs.getTimestamp(columnIndex));
    }

    @Override
    public Datetime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toDatetime(cs.getTimestamp(columnIndex));
    }

    private static Datetime toDatetime(Timestamp value) {
        return value == null ? null : new Datetime(value.getTime());
    }
}
