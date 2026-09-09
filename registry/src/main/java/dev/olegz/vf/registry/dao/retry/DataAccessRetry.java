package dev.olegz.vf.registry.dao.retry;

import java.util.function.Supplier;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.exception.ApiResultException;
import org.slf4j.Logger;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

public final class DataAccessRetry {
    private static final int MAX_DAO_REPEATS = 10;
    public static final DefaultTransactionDefinition NEW_TX_DEFINITION = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    private DataAccessRetry() {
    }

    public static void repeat(Runnable method, Supplier<String> error, Logger logger) {
        DataAccessException ex;
        for (int i = MAX_DAO_REPEATS; i >= 0; i--) {
            try {
                method.run();
                return;
            } catch (TransientDataAccessException | RecoverableDataAccessException e) {
                ex = e;
                logger.warn("DataAccessException in " + error.get() + " : " + e);
            }

            if (i == 0) throw ex;
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                throw ex;
            }
        }
    }

    public static void repeatTransaction(PlatformTransactionManager txManager, Runnable method, Supplier<String> error, Logger logger) {
        DataAccessException ex;
        TransactionStatus txStatus = null;

        for (int i = MAX_DAO_REPEATS; i >= 0; i--) {
            try {
                txStatus = txManager.getTransaction(NEW_TX_DEFINITION);
                method.run();
                txManager.commit(txStatus);
                txStatus = null;
                return;
            } catch (TransientDataAccessException | RecoverableDataAccessException e) {
                logger.warn("DataAccessException in " + error.get() + " : " + e);
                ex = e;
            } catch (ApiResultException e) {
                throw e;
            } catch (Exception e) {
                logger.error("Exception in " + error.get(), e);
                throw new ApplicationFailureException(e);
            } finally {
                if (txStatus != null) {
                    try {
                        txManager.rollback(txStatus);
                    } catch (Exception e1) {
                        logger.warn("Cannot rollback transaction", e1);
                    }
                }
            }

            if (i == 0) throw ex;
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                throw ex;
            }
        }
    }

    public static void repeatOnConcurrencyFailure(Runnable method) {
        for (int i = MAX_DAO_REPEATS; i >= 0; i--) {
            try {
                method.run();
                return;
            } catch (ConcurrencyFailureException e) {
                if (i == 0) throw e;
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException ee) {
                    throw e;
                }
            }
        }
    }

    public static <T>T repeatRead(Supplier<T> method, Supplier<String> error, Logger logger) {
        DataAccessException ex = null;
        for (int i = MAX_DAO_REPEATS; i >= 0; i--) {
            try {
                return method.get();
            } catch (RecoverableDataAccessException e) {
                ex = e;
                logger.warn("DataAccessException in " + error.get() + " : " + e);
            }
        }
        throw ex;
    }
}
