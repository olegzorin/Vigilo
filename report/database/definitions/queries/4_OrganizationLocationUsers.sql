SELECT u.location_id id,
       u.first_name AS s1,
       u.last_name as s2,
       u.email AS s3,
       u.addr_street1 AS s4
FROM v_reports_organization_users u
    JOIN organizations_hierarchy h ON h.child_organization_id = u.organization_id
                                          AND h.parent_organization_id = #{p0}
