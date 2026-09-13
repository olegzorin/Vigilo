SELECT l.location_id                             AS id,
       l.location_name                           AS s1,
       CONCAT_WS(', ', l.addr_street1, l.city,
                 CONCAT_WS(' ', l.state, l.postal_code),
                 l.country_code)                 AS s2,
       u.resident_id                                 AS n1,
       CONCAT_WS(' ', u.first_name, u.last_name) AS s3,
       u.phone                                   AS s4,
       u.email                                   AS s5
FROM locations l
         JOIN resident_locations ul ON ul.location_id = l.location_id
    AND (ul.end_date IS NULL OR ul.end_date > NOW())
         JOIN residents u ON u.resident_id = ul.resident_id
WHERE l.organization_id = #{p0} AND l.deleted_at IS NULL AND u.deleted_at IS NULL AND l.location_type = 'OPERATIONAL' AND NOT u.synthetic
