SELECT fn_reports_group_date_by_period(u.created_at, #{p3}) AS t1,
       COUNT(DISTINCT u.user_id)                            AS n1
FROM users u
WHERE u.created_at >= #{p1}
  AND u.created_at < #{p2}
  AND (#{p0} IS NULL
    OR EXISTS(SELECT 1
              FROM user_locations ul
                       JOIN v_reports_locations l ON l.location_id = ul.location_id
                       JOIN organizations_hierarchy oh ON oh.child_organization_id = l.organization_id
                  AND oh.parent_organization_id = #{p0}
              WHERE ul.user_id = u.user_id))
GROUP BY 1
ORDER BY 1
