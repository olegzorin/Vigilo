SELECT u.resident_id id, u.created_at t1
FROM v_reports_organization_residents u
WHERE u.organization_id = #{p0}
