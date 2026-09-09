SELECT u.user_id id, u.created_at t1
FROM v_reports_organization_users u, organizations_hierarchy oh
WHERE oh.child_organization_id = u.organization_id
AND oh.parent_organization_id = #{p0}
