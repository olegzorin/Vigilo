package dev.olegz.vf.core.domain.lambdarun;


/**
 * Lambda API keys are generated before running each lambda assignment.
 * The lambda key contains information about the lambda assignment and its location.
 */
public class LambdaKey {
    public final int lambdaAssignmentId;         // lambda assignment ID
    public final int lambdaId;                 // lambda ID
    public final InvocationLane lane;
    public final int locationId;            // lambda assignment location ID
    public final long variableGeneration;   // private variable namespace generation
    public int callUserId;                  // user ID received as the API call parameter
    private final LambdaKeyJwtClaims jwtClaims;

    public LambdaKey(LambdaKeyJwtClaims jwtClaims) {
        this.lambdaAssignmentId = jwtClaims.aid;
        this.lambdaId = jwtClaims.bid;
        this.lane = InvocationLane.fromCode(jwtClaims.flw);
        this.jwtClaims = jwtClaims;
        this.locationId = jwtClaims.lid;
        this.variableGeneration = jwtClaims.vgen;
    }

    @Override
    public String toString() {
        return "{lambdaAssignmentId=" + lambdaAssignmentId + ", lambdaId=" + lambdaId +
            (lane != InvocationLane.DEFAULT ? ", lane=" + lane : "") +
            ", locationId=" + locationId +
            (variableGeneration != 0 ? ", variableGeneration=" + variableGeneration : "") +
            (callUserId != 0 ? ", callUserId=" + callUserId : "") +
            '}';
    }

    public int lambdaVersionId() {
        return jwtClaims.bver;
    }
    public int triggers() {
        return jwtClaims.trs;
    }
    public int devTeamId() {
        return jwtClaims.tid;
    }
}
