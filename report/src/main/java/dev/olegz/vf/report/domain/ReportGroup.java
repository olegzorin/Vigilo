package dev.olegz.vf.report.domain;

public class ReportGroup {
    public static final int SYS_REPORT_GROUP_ID = 1;
    public static final int ORG_REPORT_GROUP_ID = 2;

    public int reportGroupId;
    public String description;
    public String name;
    public boolean analytic;
    public ReportGroupOrganization organization;
}
