SELECT ol.location_id id, ol.created_at t1
FROM v_reports_locations ol
WHERE ol.organization_id = #{p0}
