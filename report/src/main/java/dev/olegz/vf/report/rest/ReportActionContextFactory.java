package dev.olegz.vf.report.rest;

import org.springframework.stereotype.Component;

import dev.olegz.vf.common.exception.InvalidJwtException;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.registry.domain.account.UserKeyJwtClaims;
import dev.olegz.vf.registry.service.account.UserKeyService;
import dev.olegz.vf.registry.service.account.UserService;

@Component
public class ReportActionContextFactory {
    private final UserKeyService userKeyService;
    private final UserService userService;
    private final OrganizationDao organizationDao;

    public ReportActionContextFactory(UserKeyService userKeyService, UserService userService,
        OrganizationDao organizationDao)
    {
        this.userKeyService = userKeyService;
        this.userService = userService;
        this.organizationDao = organizationDao;
    }

    public ReportActionContext current(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return new ReportActionContext(null, organizationDao);
        UserKeyJwtClaims claims = userKeyService.parseUserKey(apiKey);
        User user = userService.getUser(claims.uid);
        if (user == null) throw new InvalidJwtException();
        return new ReportActionContext(user, organizationDao);
    }
}
