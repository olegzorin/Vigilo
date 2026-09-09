package dev.olegz.vf.api.monitoring;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LambdaWorkloadEndpointTest {

    @Test
    void exposesSnapshotAsReadOnlyActuatorOperation() throws NoSuchMethodException {
        Endpoint endpoint = LambdaWorkloadEndpoint.class.getAnnotation(Endpoint.class);
        Method snapshot = LambdaWorkloadEndpoint.class.getMethod("snapshot");

        assertNotNull(endpoint);
        assertEquals("lambdaworkload", endpoint.id());
        assertNotNull(snapshot.getAnnotation(ReadOperation.class));
    }
}
