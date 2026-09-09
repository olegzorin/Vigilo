-- !!! Not for production !!!
-- For test servers only
--
-- Adds the foreign keys that the main schema leaves off for performance/loading
-- reasons on real servers.
-- Referential actions (ON DELETE / ON UPDATE RESTRICT|CASCADE) are supported
-- by PostgreSQL as-is.

ALTER TABLE lambda_assignments ADD CONSTRAINT fk_lambda_assignments_location FOREIGN KEY (location_id)
      REFERENCES locations (location_id) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE user_locations ADD CONSTRAINT fk_user_locations_location FOREIGN KEY (location_id)
    REFERENCES locations (location_id) ON DELETE CASCADE ON UPDATE RESTRICT;

ALTER TABLE user_locations ADD CONSTRAINT fk_user_locations_user FOREIGN KEY (user_id)
    REFERENCES users (user_id) ON DELETE CASCADE ON UPDATE RESTRICT;

ALTER TABLE dev_team_members ADD CONSTRAINT fk_lambda_developer_team_members_user
    FOREIGN KEY (user_id) REFERENCES users(user_id)
    ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE dev_teams ADD CONSTRAINT fk_dev_teams_owner_user
    FOREIGN KEY (owner_user_id) REFERENCES users(user_id)
    ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE lambda_versions ADD CONSTRAINT fk_lambda_versions_user
    FOREIGN KEY (created_by) REFERENCES users(user_id)
    ON DELETE RESTRICT ON UPDATE RESTRICT;
