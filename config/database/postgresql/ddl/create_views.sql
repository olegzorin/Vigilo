\c vf

CREATE OR REPLACE VIEW v_reports_locations
AS
SELECT l.location_id,
       l.location_name,
       l.created_at,
       l.organization_id,
       l.addr_street1,
       l.addr_street2,
       l.city,
       l.postal_code,
       l.state,
       l.country_code,
       o.organization_name
FROM locations l
         JOIN organizations o ON o.organization_id = l.organization_id AND o.deleted_at IS NULL
WHERE l.deleted_at IS NULL AND l.location_type = 'OPERATIONAL';

CREATE OR REPLACE VIEW v_reports_devices
AS
SELECT d.device_uuid,
       d.device_name,
       d.model,
       d.type_id,
       dt.name AS device_type,
       ld.location_id
FROM devices d
         JOIN device_types dt ON dt.type_id = d.type_id
         JOIN location_devices ld ON ld.device_uuid = d.device_uuid
            AND ld.start_date <= NOW() AND (ld.end_date IS NULL OR ld.end_date > NOW());

CREATE OR REPLACE VIEW v_reports_organization_residents
AS
SELECT u.resident_id,
       u.created_at,
       u.first_name,
       u.last_name,
       u.email,
       u.phone,
       l.organization_id,
       l.location_id,
       l.addr_street1,
       l.addr_street2,
       l.city,
       l.postal_code,
       l.state,
       l.country_code
FROM locations l
         JOIN resident_locations ul ON ul.location_id = l.location_id
            AND ul.start_date <= NOW() AND (ul.end_date IS NULL OR ul.end_date > NOW())
         JOIN residents u ON u.resident_id = ul.resident_id AND u.deleted_at IS NULL
WHERE l.deleted_at IS NULL AND l.location_type = 'OPERATIONAL';
