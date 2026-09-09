package dev.olegz.vf.registry.dao.translator;

import java.sql.*;

import javax.sql.DataSource;

import org.apache.ibatis.exceptions.PersistenceException;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.transaction.TransactionException;

/**
 * Use CustomSQLErrorCodesTranslator in org.mybatis.spring.MyBatisExceptionTranslator code instead of spring SQLErrorCodeSQLExceptionTranslator.
 * The translateExceptionIfPossible() method is copied from the original MyBatisExceptionTranslator class.
 */
class CustomExceptionTranslator implements PersistenceExceptionTranslator {
    private final SQLErrorCodeSQLExceptionTranslator sqlExceptionTranslator;

    CustomExceptionTranslator(DataSource dataSource) {
        this.sqlExceptionTranslator = new SQLErrorCodeSQLExceptionTranslator(dataSource);
    }

    @Override
    public DataAccessException translateExceptionIfPossible(RuntimeException e) {
        if (e instanceof PersistenceException) {
            Throwable cause = e.getCause();
            // Batch exceptions come inside another PersistenceException
            // recursion has a risk of infinite loop so better make another if
            if (cause instanceof PersistenceException pe) {
                e = pe;
                cause = pe.getCause();
            }

            if (cause instanceof SQLException sqlEx) {
                String message = sqlEx.getMessage();
                if ((sqlEx instanceof SQLDataException) && (message != null) && message.contains("Current position is after the last row")) {
                    return new RecoverableDataAccessException(e.getMessage() + ";\n" + message, cause);
                }

                // A transient loss of the database connection or a socket timeout surfaces as a connection-level
                // SQLException. Map these to RecoverableDataAccessException so that
                // RetryOnRecoverableInterceptor retries the DAO call once the connection is back.
                if ((sqlEx instanceof SQLRecoverableException)
                    || (sqlEx instanceof SQLTransientConnectionException)
                    || (sqlEx instanceof SQLNonTransientConnectionException))
                {
                    return new RecoverableDataAccessException(e.getMessage() + ";\n" + message, sqlEx);
                }

                DataAccessException dae = customTranslate(e, sqlEx, message);
                if (dae != null) return dae;

                return sqlExceptionTranslator.translate(e.getMessage() + '\n', null, sqlEx);
            }

            if (cause instanceof TransactionException te) throw te;

            String msg = e.getMessage();
            if ((msg == null) && (cause != null)) msg = cause.getMessage();

            return new MyBatisSystemException(msg, e);
        }
        return null;
    }

    private DataAccessException customTranslate(RuntimeException e, SQLException sqlEx, String message) {
        String sqlState = sqlEx.getSQLState();
        if (sqlState == null) return null;
        return switch (sqlState) {
            case "23505" -> new DuplicateKeyException(e.getMessage() + '\n' + message, sqlEx);
            case "25006" -> new RecoverableDataAccessException(e.getMessage() + '\n' + message, sqlEx);
            default -> null;
        };
    }
}
