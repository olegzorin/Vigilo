package dev.olegz.vf.core.dao.impl;

import java.sql.Timestamp;
import java.util.List;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.exception.WrongParameterValueException;
import dev.olegz.vf.core.codec.Lz4Codec;
import dev.olegz.vf.core.dao.LambdaVariableDao;
import dev.olegz.vf.core.dao.mapper.LambdaRunMapper;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaVariable;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
public class LambdaVariableDaoImpl implements LambdaVariableDao {
    private static final Logger logger = LoggerFactory.getLogger(LambdaVariableDaoImpl.class);
    private static final int MAX_LARGE_VALUE_SIZE = 16_777_215;

    private final LambdaRunMapper mapper;

    public LambdaVariableDaoImpl(LambdaRunMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public byte[] getLambdaAssignmentVariable(int lambdaAssignmentId, long generation, String name) {
        LambdaVariable variable = mapper.selectLambdaAssignmentVariable(lambdaAssignmentId, generation, name);
        if (variable == null) return null;
        byte[] rawData = variable.value == null ? variable.largeValue : variable.value;
        if (rawData == null) return null;
        return variable.originalSize > 0 ? Lz4Codec.lz4FastUncompress(rawData, variable.originalSize) : rawData;
    }

    @Override
    public byte[] getLambdaLocationVariable(int locationId, String name) {
        LambdaVariable variable = mapper.selectLambdaLocationVariable(locationId, name);
        return (variable == null) || (variable.value == null) ? null :
            variable.originalSize > 0 ? Lz4Codec.lz4FastUncompress(variable.value, variable.originalSize) :
                variable.value;
    }

    @Override
    public void deleteLambdaAssignmentVariable(int lambdaAssignmentId, long generation, @NonNull String name) {
        mapper.deleteLambdaAssignmentVariable(lambdaAssignmentId, generation, name);
    }

    @Override
    public void deleteLambdaAssignmentVariables(int lambdaAssignmentId) {
        mapper.deleteLambdaAssignmentVariable(lambdaAssignmentId, null, null);
    }

    @Override
    public void deleteLambdaLocationVariable(int locationId, String name) {
        mapper.deleteLambdaLocationVariables(locationId, name);
    }

    @Override
    public void putLambdaAssignmentVariable(int lambdaAssignmentId, long generation, String name, byte[] value) {
        Timestamp currentDate = new Timestamp(System.currentTimeMillis());
        Exception ex = null;
        int originalSize = 0;
        byte[] smallValue = null;
        byte[] largeValue = null;

        if ((value != null) && (value.length > 0)) {
            originalSize = value.length;
            byte[] compressed = Lz4Codec.lz4Compress(value);
            if (compressed.length > LambdaVariable.MAX_SMALL_VALUE_SIZE) {
                if (compressed.length > MAX_LARGE_VALUE_SIZE) {
                    throw new WrongParameterValueException("Lambda variable is too big");
                }
                largeValue = compressed;
            } else {
                smallValue = compressed;
            }
        }

        for (int i = 0; i < 4; i++) {
            try {
                if (!mapper.updateLambdaAssignmentVariable(
                    lambdaAssignmentId, generation, name, currentDate, originalSize, smallValue, largeValue) &&
                    !mapper.insertLambdaAssignmentVariable(
                        lambdaAssignmentId, generation, name, currentDate, originalSize, smallValue, largeValue))
                {
                    throw new AccessDeniedException(
                        "Lambda private variables were reset");
                }
                return;
            } catch (PessimisticLockingFailureException | DuplicateKeyException e) {
                logger.warn("Cannot put variable for lambdaAssignmentId={}, name={}: {}", lambdaAssignmentId, name, e.toString());
                ex = e;
            }
        }
        throw new ApplicationFailureException("Cannot put variable for lambdaAssignmentId=" + lambdaAssignmentId + ", name=" + name, ex);
    }

    @Override
    public long getLambdaAssignmentVariableGeneration(int lambdaAssignmentId) {
        return mapper.selectLambdaAssignmentVariableGeneration(lambdaAssignmentId);
    }

    @Override
    public void advanceLambdaAssignmentVariableGeneration(int lambdaAssignmentId, long generation) {
        mapper.advanceLambdaAssignmentVariableGeneration(lambdaAssignmentId, generation);
    }

    @Override
    public void putLambdaLocationVariable(int locationId, String name, byte[] value) {
        Timestamp currentDate = new Timestamp(System.currentTimeMillis());
        Exception ex = null;
        int originalSize = 0;
        byte[] smallValue = null;

        if ((value != null) && (value.length > 0)) {
            originalSize = value.length;
            byte[] compressed = Lz4Codec.lz4Compress(value);
            if (compressed.length > LambdaVariable.MAX_SMALL_VALUE_SIZE) {
                throw new WrongParameterValueException("Location variable is too big");
            }
            smallValue = compressed;
        }

        for (int i = 0; i < 4; i++) {
            try {
                if (!mapper.updateLambdaLocationVariable(locationId, name, currentDate, originalSize, smallValue)) {
                    mapper.insertLambdaLocationVariable(locationId, name, currentDate, originalSize, smallValue);
                }
                return;
            } catch (PessimisticLockingFailureException | DuplicateKeyException e) {
                logger.warn("Cannot put variable for locationId={}, name={}: {}", locationId, name, e.toString());
                ex = e;
            }
        }
        throw new ApplicationFailureException("Cannot put variable for locationId=" + locationId + ", name=" + name, ex);
    }

    @Override
    public List<Integer> getInactiveAssignmentVariables(Datetime date, int maxCount) {
        return mapper.selectInactiveAssignmentVariables(date, maxCount);
    }

    @Override
    public List<Integer> getInactiveLocationVariables(Datetime date, int maxCount) {
        return mapper.selectInactiveLocationVariables(date, maxCount);
    }
}
