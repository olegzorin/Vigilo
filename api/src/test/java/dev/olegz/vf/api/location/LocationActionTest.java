package dev.olegz.vf.api.location;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BiFunction;
import dev.olegz.vf.api.account.ApiAddress;
import dev.olegz.vf.api.web.support.ActionContext;
import dev.olegz.vf.common.exception.*;
import dev.olegz.vf.registry.dao.*;
import dev.olegz.vf.registry.domain.account.*;
import dev.olegz.vf.registry.service.account.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocationActionTest {
    @Test
    void developerSeesOnlyGrantedTestingLocationsAndCannotManageThem() {
        Fixture f=new Fixture();
        assertEquals(List.of(10),f.action.listLocations(f.context(2)).locations.stream().map(l->l.locationId).toList());
        assertEquals(LocationType.TESTING,f.action.getLocation(f.context(2),10).location.locationType);
        assertThrows(AccessDeniedException.class,()->f.action.getLocation(f.context(2),11));
        assertThrows(AccessDeniedException.class,()->f.action.createLocation(f.context(2),f.create()));
        assertThrows(AccessDeniedException.class,()->f.action.deleteLocation(f.context(2),10));
        f.granted=false;
        assertTrue(f.action.listLocations(f.context(2)).locations.isEmpty());
        assertThrows(AccessDeniedException.class,()->f.action.getLocation(f.context(2),10));
    }
    @Test
    void adminManagesLocationsAndDeletionChecksResidentAndDeviceAssignments() {
        Fixture f=new Fixture();
        assertEquals(2,f.action.listLocations(f.context(1)).locations.size());
        LocationAction.Response created=f.action.createLocation(f.context(1),f.create());
        assertEquals(LocationType.TESTING,created.location.locationType);
        LocationAction.UpdateLocationRequest request=new LocationAction.UpdateLocationRequest();request.locationName="Updated";request.address=new ApiAddress(new Address());
        assertEquals("Updated",f.action.updateLocation(f.context(1),10,request).location.locationName);
        f.activeResidents=true;
        assertThrows(OperationNotAllowedException.class,()->f.action.deleteLocation(f.context(1),10));
        f.activeResidents=false;f.activeDevices=true;
        assertThrows(OperationNotAllowedException.class,()->f.action.deleteLocation(f.context(1),10));
        f.activeDevices=false;
        f.action.deleteLocation(f.context(1),10);
        assertTrue(f.deleted);
    }
    @Test
    void locationDetailsContainResidentsWithoutLoginFields() {
        Fixture f=new Fixture();
        Resident resident=new Resident();resident.residentId=8;f.residents.add(resident);
        var response=f.action.getLocation(f.context(1),11);
        assertEquals(8,response.residents.getFirst().residentId);
        var json=dev.olegz.vf.common.objectmap.StringMapper.valueToTree(response.residents.getFirst());
        assertFalse(json.has("username"));assertFalse(json.has("password"));assertFalse(json.has("userId"));
    }
    @Test
    void locationDetailsExposeGrantedTeamIdentifiersAndNames() {
        Fixture f = new Fixture();
        var team = new dev.olegz.vf.core.domain.lambdaversion.DevTeam(2, "Testing team", null);
        team.devTeamId = 7;
        f.teams.add(team);
        var location = f.action.getLocation(f.context(1), 10).location;
        assertEquals(7, location.grantedTeams.getFirst().devTeamId());
        assertEquals("Testing team", location.grantedTeams.getFirst().name());
    }
    private static class Fixture {
        boolean granted=true,activeResidents,activeDevices,deleted;
        final List<dev.olegz.vf.core.domain.lambdaversion.DevTeam> teams=new ArrayList<>();
        final List<Resident> residents=new ArrayList<>();
        final List<Location> locations=new ArrayList<>(List.of(location(10,LocationType.TESTING),location(11,LocationType.OPERATIONAL)));
        final OrganizationDao orgs=proxy(OrganizationDao.class,(n,a)->{Organization o=new Organization();o.adminUserId=1;return o;});
        final LocationDao dao=proxy(LocationDao.class,(n,a)->switch(n){
            case "getOrganizationLocation" -> locations.stream().filter(l->l.locationId==(int)a[1] && l.organizationId==(int)a[0]).findFirst().orElse(null);
            case "getLocationsByOrganization" -> locations;
            case "insertLocation" -> {Location l=(Location)a[0];l.locationId=20;locations.add(l);yield null;}
            case "updateLocation" -> true;
            case "deleteLocation" -> {deleted=true;yield true;}
            default -> null;
        });
        final AccessService access=new AccessService(orgs,dao,List.of((u,l)->granted && u==2 && l==10));
        final LocationAction action=new LocationAction(proxy(dev.olegz.vf.core.service.dev.DevTeamsService.class,(n,a)->teams),access,dao,
            proxy(DeviceDao.class,(n,a)->activeDevices),proxy(ResidentLocationDao.class,(n,a)->activeResidents),
            proxy(ResidentDao.class,(n,a)->residents),proxy(ResidentLocationAssignmentService.class,(n,a)->null));
        ActionContext context(int id){User u=new User();u.userId=id;u.organizationId=100;return new ActionContext(u,orgs);}
        LocationAction.CreateLocationRequest create(){var r=new LocationAction.CreateLocationRequest();r.organizationId=100;r.locationName="Test";r.locationType=LocationType.TESTING;r.address=new ApiAddress(new Address());return r;}
    }
    private static Location location(int id,LocationType type){Location l=new Location();l.locationId=id;l.organizationId=100;l.locationType=type;l.address=new Address();return l;}
    private static <T>T proxy(Class<T> t,BiFunction<String,Object[],Object> h){return t.cast(Proxy.newProxyInstance(t.getClassLoader(),new Class<?>[]{t},(p,m,a)->h.apply(m.getName(),a)));}
}
