package dev.olegz.vf.registry.dao.handlers;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.exception.WrongParameterValueException;
import dev.olegz.vf.common.objectmap.BytesMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

public abstract class JsonTypeHandler<T> extends BaseTypeHandler<T> {
    private static final Logger logger = LoggerFactory.getLogger(JsonTypeHandler.class);

    static final JsonMapper mapper = BytesMapper.buildDefaultMapper();

    protected abstract String typeName();

    protected abstract JavaType javaType();

    protected T readStringValue(String value) {
        return mapper.readValue(value, javaType());
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
        String value;
        try {
            value = mapper.writeValueAsString(parameter);
        } catch (Exception e) {
            throw new ApplicationFailureException("Failed to write JSON string\n" + parameter, e);
        }
        if (!isValidVarchar(value)) {
            throw new WrongParameterValueException("Unsupported characters in JSON parameter\n" + parameter);
        }
        setJsonb(ps, i, value);
    }

    @Override
    public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value == null || value.isBlank() ? null : readValue(value, rs, columnName, 0);
    }

    @Override
    public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null || value.isBlank() ? null : readValue(value, rs, null, columnIndex);
    }

    @Override
    public T getNullableResult(CallableStatement cs, int columnIndex) {
        return null;
    }

    private T readValue(String value, ResultSet rs, String columnName, int columnIndex) throws SQLException {
        try {
            return readStringValue(value);
        } catch (Exception e) {
            var md = rs.getMetaData();
            if (columnName != null) {
                columnIndex = rs.findColumn(columnName);
            } else {
                columnName = md.getColumnName(columnIndex);
            }

            logger.error("Cannot read value as " + typeName() +
                '\n' + md.getTableName(columnIndex) + '.' + columnName + '=' + value);
        }
        return null;
    }

    public static boolean isValidVarchar(String input) {
        if (input == null) return true;

        // PostgreSQL strictly forbids the null byte character
        if (input.indexOf('\u0000') != -1) {
            return false;
        }

        return true;
    }

    static void setJsonb(PreparedStatement ps, int index, String value) throws SQLException {
        PGobject json = new PGobject();
        json.setType("jsonb");
        json.setValue(value);
        ps.setObject(index, json);
    }
}
