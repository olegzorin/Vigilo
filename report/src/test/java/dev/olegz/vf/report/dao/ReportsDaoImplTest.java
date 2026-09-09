package dev.olegz.vf.report.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.olegz.vf.report.dao.impl.ReportsDaoImpl;
import dev.olegz.vf.report.dao.mapper.ReportsMapper;

class ReportsDaoImplTest {
    @Test
    void insertReportGroupOrganizationRemovesDescendantAssignments() {
        List<Object[]> calls = new ArrayList<>();
        ReportsMapper mapper = (ReportsMapper) Proxy.newProxyInstance(
            ReportsMapper.class.getClassLoader(), new Class<?>[] {ReportsMapper.class}, (proxy, method, args) -> {
                calls.add(new Object[] {method.getName(), args});
                if ("insertReportGroupOrganization".equals(method.getName())) return true;
                if ("deleteReportGroupOrganizations".equals(method.getName())) return true;
                throw new UnsupportedOperationException(method.getName());
            });

        new ReportsDaoImpl(mapper).insertReportGroupOrganization(7, 12);

        assertEquals("insertReportGroupOrganization", calls.get(0)[0]);
        assertEquals("deleteReportGroupOrganizations", calls.get(1)[0]);
        Object[] deleteArguments = (Object[]) calls.get(1)[1];
        assertEquals(7, deleteArguments[0]);
        assertEquals(12, deleteArguments[1]);
        assertEquals(true, deleteArguments[2]);
    }
}
