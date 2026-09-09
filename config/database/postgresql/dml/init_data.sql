/*==============================================================*/
/* Initial PostgreSQL database objects */
/*==============================================================*/
\c vf

/*==============================================================*/
/* Step 1: Demo organization                                    */
/*==============================================================*/
INSERT INTO organizations (organization_id, organization_name, created_at, country_code, city, timezone)
VALUES (1, 'Demo Organization', NOW(), 'US', 'New York', 'America/New_York');

/*==============================================================*/
/* Step 2: Demo admin user                                      */
/*==============================================================*/
INSERT INTO users (user_id, username, created_at, first_name, last_name, email, organization_id)
VALUES (1, 'admin@demo.org', NOW(), 'Demo', 'Admin', 'admin@demo.org', 1);

-- Designate the user as administrator of the demo organization
UPDATE organizations SET admin_user_id = 1 WHERE organization_id = 1;

/*==============================================================*/
/* Step 3: Demo location                                        */
/*==============================================================*/
INSERT INTO locations (location_id, location_name, created_at, organization_id, timezone)
VALUES (1, 'Demo Location', NOW(), 1, 'America/New_York');

/*==============================================================*/
/* Step 4: Demo dev team                                        */
/*==============================================================*/
INSERT INTO dev_teams (dev_team_id, owner_user_id, name, description)
VALUES (1, 1, 'Demo Dev Team', 'Demo development team');

/*==============================================================*/
/* Step 5: Add the demo admin user as a team member             */
/*==============================================================*/
INSERT INTO dev_team_members (dev_team_id, user_id, start_date)
VALUES (1, 1, NOW());

/*==============================================================*/
/* Step 6: Demo lambda                                             */
/*==============================================================*/
INSERT INTO lambdas (lambda_id, name, description, dev_team_id, created_at)
VALUES (1, 'Demo Lambda', 'Demo lambda', 1, NOW());

/*==============================================================*/
/* Step 7: Smart lock device type                               */
/*==============================================================*/
INSERT INTO device_types (type_id, name)
VALUES (1, 'Smart Lock');

/*==============================================================*/
/* Step 8: Demo smart lock                                      */
/*==============================================================*/
INSERT INTO devices (
  device_uuid, organization_id, type_id, device_name, serial_number,
  model, vendor, manufacturer)
VALUES (
  '807124fe-6342-5bdf-a8ce-58e262911afd', 1, 1, 'Demo Front Door Lock', NULL,
  'WLCKB2', 'WYZE', 'Wyze');

/*==============================================================*/
/* Step 9: Demo smart lock current state                        */
/*==============================================================*/
INSERT INTO device_current_states (device_uuid, current_state, measured_at, received_at)
VALUES (
  '807124fe-6342-5bdf-a8ce-58e262911afd',
  '{"lockState":"locked","batteryLevel":100,"wifiConnected":true,"autoLockEnabled":true,"tamperAlarmActive":false}'::jsonb,
  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

/*==============================================================*/
/* Step 10: Advance identity sequences past the explicit ids    */
/* above. PostgreSQL GENERATED ... AS IDENTITY does not bump the */
/* sequence on explicit inserts,                                 */
/* so the next generated id would otherwise collide with id 1.   */
/*==============================================================*/
SELECT setval(pg_get_serial_sequence('organizations', 'organization_id'),
              (SELECT MAX(organization_id) FROM organizations));
SELECT setval(pg_get_serial_sequence('users', 'user_id'),
              (SELECT MAX(user_id) FROM users));
SELECT setval(pg_get_serial_sequence('locations', 'location_id'),
              (SELECT MAX(location_id) FROM locations));
SELECT setval(pg_get_serial_sequence('dev_teams', 'dev_team_id'),
              (SELECT MAX(dev_team_id) FROM dev_teams));
SELECT setval(pg_get_serial_sequence('lambdas', 'lambda_id'),
              (SELECT MAX(lambda_id) FROM lambdas));
