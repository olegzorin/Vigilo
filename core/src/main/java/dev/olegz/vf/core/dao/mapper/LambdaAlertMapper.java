package dev.olegz.vf.core.dao.mapper;

import dev.olegz.vf.core.domain.alert.LambdaAlert;
import org.apache.ibatis.annotations.Param;

public interface LambdaAlertMapper {
    int insertAlert(LambdaAlert alert);

    LambdaAlert selectAlert(
        @Param("idempotencyKey") String idempotencyKey,
        @Param("lambdaAssignmentId") int lambdaAssignmentId);
}
