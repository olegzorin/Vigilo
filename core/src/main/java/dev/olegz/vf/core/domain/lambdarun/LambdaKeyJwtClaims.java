package dev.olegz.vf.core.domain.lambdarun;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.olegz.vf.registry.service.encryption.JwtClaims;

public class LambdaKeyJwtClaims extends JwtClaims {
    public int aid; // lambda assignment ID
    public int bid; // lambda ID
    public int tid; // dev team ID
    public int bver; // lambda version ID
    public int trs; // triggers
    public int lid; // location ID
    @JsonInclude(value = JsonInclude.Include.NON_DEFAULT)
    public long vgen; // private variable generation
    @JsonInclude(value = JsonInclude.Include.NON_DEFAULT)
    public byte flw; // flow

    public LambdaKeyJwtClaims() {
    }

    public LambdaKeyJwtClaims(
        LambdaRuntimeAssignment lambda,
        long expiry,
        InvocationLane lane,
        int triggers,
        long variableGeneration)
    {
        this.ty = JwtClaims.TYPE_LAMBDA;
        this.exp = Instant.now().getEpochSecond() + expiry;
        this.aid = lambda.lambdaAssignmentId;
        this.bid = lambda.lambdaId;
        this.tid = lambda.developerTeamId;
        this.bver = lambda.version.lambdaVersionId;
        this.trs = triggers;
        this.lid = lambda.locationId;
        this.vgen = variableGeneration;
        this.flw = lane.code();
    }

    @Override
    public boolean valid() {
        return (aid != 0) && (bid != 0) && (ty == JwtClaims.TYPE_LAMBDA);
    }
}
