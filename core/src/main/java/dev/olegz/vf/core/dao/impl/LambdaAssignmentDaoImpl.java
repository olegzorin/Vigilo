package dev.olegz.vf.core.dao.impl;

import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.exception.DuplicateEntityException;
import dev.olegz.vf.registry.cache.CacheNames;
import dev.olegz.vf.core.dao.LambdaAssignmentDao;
import dev.olegz.vf.core.dao.mapper.LambdaAssignmentMapper;
import dev.olegz.vf.registry.dao.retry.RetryOnConcurrencyFailure;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaAssignment;
import dev.olegz.vf.core.domain.lambdarun.LambdaRun;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class LambdaAssignmentDaoImpl implements LambdaAssignmentDao {
    private static final Logger logger = LoggerFactory.getLogger(LambdaAssignmentDaoImpl.class);

    private final LambdaAssignmentMapper mapper;

    public LambdaAssignmentDaoImpl(LambdaAssignmentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @RetryOnConcurrencyFailure("")
    @Transactional(propagation = Propagation.REQUIRED)
    @CacheEvict(cacheNames = CacheNames.LAMBDA_RUNTIME_ASSIGNMENTS, key = "#lambdaAssignment.locationId")
    public void insertLambdaAssignment(LambdaAssignment lambdaAssignment) {
        logger.debug(">insertLambdaAssignment() lambdaAssignment={}", lambdaAssignment);
        List<LambdaAssignment> lambdaAssignments = mapper.selectConcurrentLambdaAssignments(lambdaAssignment);
        if ((lambdaAssignments != null) && !lambdaAssignments.isEmpty()) {
            throw new DuplicateEntityException("Lambda assignment already exists");
        }

        mapper.insertLambdaAssignment(lambdaAssignment);
        mapper.insertLambdaAssignmentRun(new LambdaRun(lambdaAssignment.lambdaAssignmentId, InvocationLane.DEFAULT));
        logger.debug("<insertLambdaAssignment()");
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.LAMBDA_RUNTIME_ASSIGNMENTS, key = "#lambdaAssignment.locationId")
    public boolean updateLambdaAssignment(LambdaAssignment lambdaAssignment) {
        logger.debug(">updateLambdaAssignment() lambdaAssignment={}", lambdaAssignment);
        boolean updated = mapper.updateLambdaAssignment(lambdaAssignment);
        logger.debug("<updateLambdaAssignment() lambdaAssignmentId={}, updated={}", lambdaAssignment.lambdaAssignmentId, updated);
        return updated;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.LAMBDA_RUNTIME_ASSIGNMENTS, allEntries = true)
    public boolean markLambdaAssignmentDeleted(int lambdaAssignmentId) {
        return mapper.markLambdaAssignmentDeleted(lambdaAssignmentId, Datetime.now());
    }

    @Override
    public LambdaAssignment getLambdaAssignment(int lambdaAssignmentId) {
        return mapper.selectLambdaAssignment(lambdaAssignmentId);
    }

    @Override
    public List<Integer> getLambdaAssignmentIds(int lambdaId) {
        return mapper.selectLambdaAssignmentIdsByLambdaId(lambdaId);
    }

    @Override
    public List<Integer> getLambdaAssignmentIdsForLocation(int locationId) {
        return mapper.selectLambdaAssignmentIdsByLocationId(locationId);
    }

    @Override
    public List<LambdaAssignment> getLambdaAssignments(Integer lambdaId, Integer locationId, boolean testingOnly) {
        return mapper.selectLambdaAssignments(lambdaId, locationId, testingOnly);
    }

    @Override
    public void resetLambdaAssignmentRunData(int lambdaAssignmentId) {
        mapper.updateLambdaAssignmentRunsReset(lambdaAssignmentId);
    }

}
