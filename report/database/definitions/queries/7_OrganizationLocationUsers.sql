SELECT l.location_id                             AS id,
       l.location_name                           AS s1,
       CONCAT_WS(', ', l.addr_street1, l.city,
                 CONCAT_WS(' ', l.state, l.postal_code),
                 l.country_code)                 AS s2,
       u.user_id                                 AS n1,
       CONCAT_WS(' ', u.first_name, u.last_name) AS s3,
       u.phone                                   AS s4,
       u.email                                   AS s5
FROM organizations_hierarchy oh
         JOIN locations l ON l.organization_id = oh.child_organization_id
    AND l.deleted_at IS NULL
         JOIN user_locations ul ON ul.location_id = l.location_id
    AND (ul.end_date IS NULL OR ul.end_date > NOW())
         JOIN users u ON u.user_id = ul.user_id
WHERE oh.parent_organization_id = #{p0}
