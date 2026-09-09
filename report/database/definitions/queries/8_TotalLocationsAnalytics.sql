SELECT
    COUNT(distinct l.location_id) n1
FROM v_reports_locations l
    JOIN organizations_hierarchy h ON h.child_organization_id = l.organization_id AND h.parent_organization_id = #{p0}
