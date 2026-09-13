SELECT COUNT(1) n1
FROM residents
WHERE organization_id = #{p0}
  AND deleted_at IS NULL AND NOT synthetic
;;;
SELECT COUNT(1) n2
FROM lambda_assignments ba
         JOIN locations l ON ba.location_id = l.location_id
WHERE l.organization_id = #{p0} AND l.location_type = 'OPERATIONAL'
  AND ba.deleted_at IS NULL
  AND (ba.end_date IS NULL OR ba.end_date > NOW())
;;;

SELECT COUNT(1) n3
FROM location_devices ld
         JOIN locations l ON l.location_id = ld.location_id
WHERE l.organization_id = #{p0} AND l.location_type = 'OPERATIONAL'
  AND (ld.end_date IS NULL OR ld.end_date > NOW())
