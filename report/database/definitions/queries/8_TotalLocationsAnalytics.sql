SELECT
    COUNT(distinct l.location_id) n1
FROM v_reports_locations l
WHERE l.organization_id = #{p0}
