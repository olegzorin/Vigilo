SELECT
    di.device_type s1,
    (CASE WHEN #{p1} THEN COALESCE(di.model, '') ELSE '' END) s2,
    COUNT(1) n1
FROM v_reports_devices di
    JOIN v_reports_locations l ON l.location_id = di.location_id
    JOIN organizations_hierarchy h ON h.child_organization_id = l.organization_id AND h.parent_organization_id = #{p0}
GROUP BY 1, 2
