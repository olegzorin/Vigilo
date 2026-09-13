# Residents, login accounts, and developer access

Residents are monitored people, not application users. A resident belongs to an organization,
may have one active location assignment, and has no username, password, API key, or permissions.
Administrators register and edit residents through `/vf/residents` and assign them through
`/vf/locations/{locationId}/residents/{residentId}`. Assignment history is retained.

Login accounts remain in `users`. `/vf/users` creates DEVELOPER accounts and requires the
organization administrator. Authentication returns a profile and API key, without a resident
location. Organization administration is designated by `organizations.admin_user_id`; account
category alone does not grant administration. Administrators are provisioned during organization
setup. Existing administrators who are also developers can retain both responsibilities.

## Organization and team boundaries

- Administrators manage their own organization's accounts, residents, locations, devices, teams,
  membership, ownership, testing grants, and reports. Parent organizations receive no implicit API access.
- A developer team belongs to one organization. Its owner and members must be developer accounts
  in that organization. Developers cannot create teams or change membership or location grants.
- Creating a team requires `ownerUserId` and atomically creates an independent TESTING location,
  adds the owner as an active member, and grants the location to the team.
- Administrators grant additional testing locations using
  `PUT /vf/teams/{devTeamId}/testing-locations/{locationId}` and revoke grants with DELETE.
  `testingLocationId` identifies the provisioned default, not an irrevocable permission.
- Ownership transfer uses `PUT /vf/teams/{devTeamId}/owner/{userId}`. The new owner must already
  be an active member. The current owner cannot be removed until ownership is transferred.
- Developers can list and inspect their active teams and their teams' granted testing locations.
  Removed or expired members lose inherited access. Manually issued lambda credentials also
  recheck the issuing account, location access, and lambda-team membership on every request.
- Team members develop code and test versions. Production promotion, rollback, and deletion of
  active versions require the team owner. Published production versions can be assigned by
  organization administrators; publication does not grant access to a customer's locations.

## Testing isolation

Locations have an immutable `locationType`: OPERATIONAL or TESTING. A team grant is valid only
for a TESTING location in the same organization. Login accounts are never assigned as residents.
Real resident records may only be assigned to operational locations; `synthetic: true` records
may only be assigned to testing locations. Devices similarly have an immutable `testing` flag.
Operational devices cannot be reassigned into testing, exposing their retained state.

Developers must select an authorized testing location when listing devices. Lambda assignment
creation and management require both access to the location and membership in the lambda's team;
organization administrators manage assignments within their organization. Test versions cannot
be created or re-enabled at operational locations. A testing location can run a published version
for comparison. Bulk cancellation only touches assignments within the caller's access scope,
including assignments whose versions have since been removed.

Lambda inputs expose `location.locationType` and `locationResidents[].residentId`. The former
`locationUsers` and `locationAccess` input fields are removed. Resident identifiers confer no access.
Variables and state retain their existing assignment/location scope. Alert persistence derives
`testing` from the actual location, and alert responses include it. Operational report definitions
exclude testing locations and synthetic residents and scope queries to the selected organization.
Reports require organization administration; an omitted organization defaults to the caller's own.

Testing locations are a VF data/access boundary, not an operating-system or network sandbox for
Python code. VF's alert API stores alerts; it does not implement resident alert delivery. Consumers
must route `testing` alerts to test destinations. External services called directly by a plugin
need separately configured test credentials and destinations; location type cannot intercept those calls.

## Upgrade procedure

This is a breaking schema, REST, and lambda-input change. Do not run mixed old/new processes.

1. Stop API and worker processes and drain in-flight lambda work. Review the classification in
   `config/database/postgresql/migrations/20260912_residents_and_developer_access.sql`.
2. Apply that migration manually before deploying the new code. It preserves resident IDs and
   location history, clears credentials and soft-deletes former resident login accounts, and
   retains administrator/team login accounts. Legacy assigned administrators/developers also
   receive separate resident records so their assignment history is not silently discarded;
   review these records for your deployment. Cross-organization team membership causes the
   whole transaction to fail and must be reconciled before retrying.
3. Existing locations become OPERATIONAL. Each existing team receives a new testing location.
   Existing testing assignments are disabled; recreate them on testing locations with synthetic
   residents and explicitly designated test devices. Existing operational data is not copied.
4. Update Python plugins for `locationResidents`, deploy the application, and register the updated
   report definitions before restoring API/report traffic:
   `mvn -q -pl report -am -DskipTests -Pregister-reports verify`.
   Report IDs are preserved. Previously generated report files/history are retained.
5. Reissue lambda API keys. The authorization-model version rejects legacy keys that did not
   distinguish manually issued credentials from runtime credentials. Restart workers so their
   in-memory caches and serialized input contracts reflect the new model.

The migration does not run automatically on application startup.

### Inspecting testing-location grants

`GET /vf/teams/{devTeamId}` returns `team.testingLocations`, containing every currently
granted, non-deleted testing location. The provisioned `testingLocationId` remains metadata
even after its grant is revoked; use `testingLocations` to inspect current access.

`GET /vf/locations/{locationId}` returns `location.grantedTeams` with each team's
`devTeamId` and `name`. Organization administrators see all granted teams in their organization.
Developers see only teams in which they are active members. Operational locations return an
empty list. Adding or removing team members changes the access they inherit from these grants.
