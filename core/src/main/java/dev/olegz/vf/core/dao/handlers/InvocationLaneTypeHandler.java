package dev.olegz.vf.core.dao.handlers;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public class InvocationLaneTypeHandler extends BaseTypeHandler<InvocationLane> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, InvocationLane lane, JdbcType jdbcType)
        throws SQLException {
        ps.setString(i, lane.name());
    }

    @Override
    public InvocationLane getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String lane = rs.getString(columnName);
        return lane == null ? null : InvocationLane.valueOf(lane);
    }

    @Override
    public InvocationLane getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String lane = rs.getString(columnIndex);
        return lane == null ? null : InvocationLane.valueOf(lane);
    }

    @Override
    public InvocationLane getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String lane = cs.getString(columnIndex);
        return lane == null ? null : InvocationLane.valueOf(lane);
    }
}
