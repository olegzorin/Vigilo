package dev.olegz.vf.core.service.lambda;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BiFunction;
import dev.olegz.vf.common.exception.*;
import dev.olegz.vf.core.dao.*;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaAssignment;
import dev.olegz.vf.core.domain.lambdaversion.*;
import dev.olegz.vf.registry.dao.*;
import dev.olegz.vf.registry.domain.account.*;
import dev.olegz.vf.registry.service.account.AccessService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaAssignmentServiceImplTest {
    @org.junit.jupiter.api.BeforeAll static void initialize() { dev.olegz.vf.common.props.PropertyStore.start(); }
    @Test
    void testingRequiresLocationGrantAndLambdaMembershipAndCannotUseOperationalLocations() {
        Fixture f = new Fixture();
        assertThrows(AccessDeniedException.class, () -> f.service.createLambdaAssignment(f.developer, 7, 10, true));
        f.granted = true;
        f.service.createLambdaAssignment(f.developer, 7, 10, true);
        assertEquals(1, f.resets);
        f.member = false;
        assertThrows(AccessDeniedException.class, () -> f.service.createLambdaAssignment(f.developer, 7, 10, true));
        assertThrows(AccessDeniedException.class, () -> f.service.createLambdaAssignment(f.admin, 7, 11, true));
        f.service.createLambdaAssignment(f.admin, 7, 11, false);
        assertEquals(2, f.resets);
        assertThrows(ObjectNotFoundException.class, () -> f.service.createLambdaAssignment(f.admin, 7, 12, false));
    }
    @Test
    void bulkCancellationIsRestrictedToCallerOrganizationAndAuthorizedTestingLocations() {
        Fixture f = new Fixture();
        f.assignments.addAll(List.of(new LambdaAssignment(7,10,true), new LambdaAssignment(7,11,false),new LambdaAssignment(7,12,false)));
        for (int i=0;i<f.assignments.size();i++) f.assignments.get(i).lambdaAssignmentId=i+1;
        f.granted=true;
        assertEquals(1,f.service.getLambdaAssignmentsForLambda(f.developer,7).size());
        f.service.cancelAssignmentsForLambda(f.developer,7);
        assertEquals(List.of(1),f.deleted);
        f.deleted.clear();
        f.service.cancelAssignmentsForLambda(f.admin,7);
        assertEquals(List.of(1,2),f.deleted);
        f.deleted.clear();
        f.member=false;
        f.service.cancelAssignmentsForLocation(f.developer,10);
        assertTrue(f.deleted.isEmpty());
    }
    @Test
    void readEnableAndCancelUseTheSameLocationAndTeamBoundary() {
        Fixture f=new Fixture();
        LambdaAssignment assignment=new LambdaAssignment(7,10,true);assignment.lambdaAssignmentId=1;assignment.lambda=f.lambda;
        f.assignments.add(assignment);
        assertThrows(AccessDeniedException.class,()->f.service.getLambdaAssignment(f.developer,1));
        f.granted=true;
        assertSame(assignment,f.service.getLambdaAssignment(f.developer,1));
        f.service.setLambdaAssignmentEnabled(f.developer,1,false);
        assertFalse(assignment.checkActive());
        f.service.setLambdaAssignmentEnabled(f.developer,1,true);
        assertEquals(1,f.resets);
        f.granted=false;
        assertThrows(AccessDeniedException.class,()->f.service.cancelLambdaAssignment(f.developer,1));
        assertThrows(AccessDeniedException.class,()->f.service.setLambdaAssignmentEnabled(f.developer,1,true));
        f.service.cancelLambdaAssignment(f.admin,1);
        assertEquals(List.of(1),f.deleted);
    }
    private static class Fixture {
        final User admin=user(1), developer=user(2);
        final Lambda lambda=new Lambda();
        final List<LambdaAssignment> assignments=new ArrayList<>();
        final List<Integer> deleted=new ArrayList<>();
        boolean granted, member=true;
        int resets;
        Fixture(){lambda.lambdaId=7;lambda.devTeamId=3;}
        final OrganizationDao organizations=proxy(OrganizationDao.class,(n,a)->{Organization o=new Organization();o.adminUserId=1;return o;});
        final LocationDao locations=proxy(LocationDao.class,(n,a)-> {
            if(!n.equals("getOrganizationLocation") || (int)a[1]==12)return null;
            Location l=new Location();l.locationId=(int)a[1];l.organizationId=100;l.locationType=l.locationId==10?LocationType.TESTING:LocationType.OPERATIONAL;return l;
        });
        final AccessService access=new AccessService(organizations,locations,List.of((u,l)->granted && l==10));
        final DevTeamDao teams=proxy(DevTeamDao.class,(n,a)-> {
            if(n.equals("checkDevTeamMember"))return member;
            if(n.equals("getDevTeam")){DevTeam t=new DevTeam(2,"T",null);t.organizationId=100;return t;}
            return null;
        });
        final LambdaDao lambdas=proxy(LambdaDao.class,(n,a)-> {
            if(n.equals("getLambda"))return lambda;
            if(n.equals("getLambdaVersions")){LambdaVersion v=new LambdaVersion();v.published=true;v.status=LambdaVersionStatus.PRODUCTION;return List.of(v);}
            return null;
        });
        final LambdaAssignmentDao dao=proxy(LambdaAssignmentDao.class,(n,a)->switch(n){
            case "insertLambdaAssignment" -> {assignments.add((LambdaAssignment)a[0]);yield null;}
            case "getLambdaAssignments" -> assignments.stream().filter(x->a[1]==null || x.locationId==(int)a[1]).toList();
            case "getLambdaAssignment" -> assignments.stream().filter(x->x.lambdaAssignmentId==(int)a[0]).findFirst().orElse(null);
            case "markLambdaAssignmentDeleted" -> {deleted.add((int)a[0]);yield true;}
            case "updateLambdaAssignment" -> true;
            default -> null;
        });
        final LambdaAssignmentServiceImpl service=new LambdaAssignmentServiceImpl(lambdas,dao,
            proxy(LambdaAssignmentCleanupService.class,(n,a)->null),locations,organizations,teams,
            proxy(LambdaClientService.class,(n,a)->{if(n.equals("sendResetEvent"))resets++;return null;}),access);
    }
    private static User user(int id){User u=new User();u.userId=id;u.organizationId=100;return u;}
    private static <T>T proxy(Class<T> type,BiFunction<String,Object[],Object> h){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->h.apply(m.getName(),a)));}
}
