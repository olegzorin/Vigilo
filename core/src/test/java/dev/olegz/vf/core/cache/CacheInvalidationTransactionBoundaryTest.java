package dev.olegz.vf.core.cache;

import java.lang.reflect.Method;

import dev.olegz.vf.core.dao.impl.*;
import dev.olegz.vf.registry.dao.impl.DeviceDaoImpl;
import dev.olegz.vf.registry.dao.impl.LocationDaoImpl;
import dev.olegz.vf.registry.dao.impl.OrganizationDaoImpl;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CacheInvalidationTransactionBoundaryTest {

    @Test
    void everyInvalidatingDaoWriteHasItsOwnTransactionBoundary() {
        Class<?>[] daoClasses = {
            LambdaAssignmentDaoImpl.class,
            LambdaDaoImpl.class,
            DeviceDaoImpl.class,
            LocationDaoImpl.class,
            OrganizationDaoImpl.class
        };

        for (Class<?> daoClass : daoClasses) {
            for (Method method : daoClass.getDeclaredMethods()) {
                if (method.getAnnotation(CacheEvict.class) == null) continue;
                assertNotNull(method.getAnnotation(Transactional.class),
                    () -> daoClass.getSimpleName() + "." + method.getName() +
                        " must be transactional so its outbox insert is atomic with the database write");
            }
        }
    }
}
