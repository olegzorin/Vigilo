\c vf

-- Reports utility functions

DROP FUNCTION IF EXISTS fn_reports_group_date_by_period(date, integer);

-- Function to group dates by period for reports
-- period: 1 = Daily, 2 = Weekly (Sunday start), 3 = Monthly, 4 = Annual
-- Returns the start date of the period containing the input date
CREATE FUNCTION fn_reports_group_date_by_period(
    p_date date,
    p_period integer
) RETURNS date
    LANGUAGE sql
    IMMUTABLE
    STRICT
AS $$
    SELECT CASE p_period
        WHEN 1 THEN p_date
        WHEN 2 THEN p_date - EXTRACT(DOW FROM p_date)::integer
        WHEN 3 THEN date_trunc('month', p_date)::date
        WHEN 4 THEN make_date(EXTRACT(YEAR FROM p_date)::integer, 1, 1)
    END
$$;
