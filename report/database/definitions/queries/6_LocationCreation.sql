SELECT ol.location_id id, ol.created_at t1
FROM v_reports_locations ol, organizations_hierarchy oh
WHERE oh.child_organization_id = ol.organization_id
AND oh.parent_organization_id = #{p0}
