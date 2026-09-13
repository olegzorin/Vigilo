SELECT fn_reports_group_date_by_period(CAST(u.created_at AS date), #{p3}) AS t1,
       COUNT(DISTINCT u.resident_id)                            AS n1
FROM residents u
WHERE NOT u.synthetic AND u.created_at >= #{p1}
  AND u.created_at < #{p2}
  AND u.organization_id = #{p0}
GROUP BY 1
ORDER BY 1
