package dev.olegz.vf.registry.tool;

import dev.olegz.vf.common.ShutdownManager;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.registry.config.DataSourceConfig;
import dev.olegz.vf.registry.config.RegistryConfig;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.domain.account.Organization;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.registry.service.account.UserService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Command-line tool that creates a user and makes it the administrator of a given organization,
 * bypassing the REST API.
 * <p>
 * This is the bootstrap path for an organization's first administrator: the {@code createUser}
 * REST endpoint requires the caller to already be an admin, so the very first admin of an org can
 * only be created out of band. Because credentials are Argon2id-hashed by {@link UserService},
 * a plain SQL insert cannot produce a usable password — this tool reuses the service so the hash,
 * password-strength checks and username-uniqueness check all match the running application.
 * <p>
 * "Administrator" means the user referenced by {@code organizations.admin_user_id} (see
 * {@code ActionContext.requireAdmin}); the tool creates the user in the target org and then points
 * that column at it. The user insert and the org update run in a single transaction.
 * <p>
 * Run it via the {@code create-admin} Maven profile (see {@code registry/pom.xml}):
 * <pre>
 *   mvn -q -pl registry -am -Pcreate-admin exec:java \
 *       -Dexec.args="--org 42 --username jane --firstName Jane --lastName Doe --email jane@acme.com"
 * </pre>
 * The password may be supplied with {@code --password}; when omitted it is read from the
 * {@code VF_ADMIN_PASSWORD} environment variable, and failing that prompted for on the console
 * (so it never lands in the shell history). Pass {@code --force} to replace an org's existing admin.
 */
public final class CreateAdminTool {

    private CreateAdminTool() {
    }

    public static void main(String[] args) {
        CliArgs opts = CliArgs.parse(args);

        int organizationId = opts.requireInt("org");
        String username = opts.require("username");
        String password = opts.resolvePassword("Password for new admin: ");
        boolean force = opts.has("force");

        PropertyStore.start();

        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(RegistryConfig.class, DataSourceConfig.class)) {

            UserService userService = ctx.getBean(UserService.class);
            OrganizationDao organizationDao = ctx.getBean(OrganizationDao.class);
            TransactionTemplate tx = new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class));

            int userId = tx.execute(_ -> {
                Organization org = organizationDao.getOrganization(organizationId);
                if (org == null) {
                    throw new IllegalArgumentException("Organization " + organizationId + " not found");
                }
                if (org.adminUserId != null && !force) {
                    throw new IllegalArgumentException("Organization " + organizationId
                            + " already has admin user " + org.adminUserId + "; pass --force to replace it");
                }

                User user = new User();
                user.username = username;
                user.firstName = opts.get("firstName");
                user.lastName = opts.get("lastName");
                user.email = opts.get("email");
                user.phone = opts.get("phone");
                user.organizationId = organizationId;
                userService.createUser(user, password); // hashes + validates + checks uniqueness

                org.adminUserId = user.userId;
                organizationDao.updateOrganization(org);
                return user.userId;
            });

            System.out.println("Created admin user " + userId + " ('" + username
                    + "') for organization " + organizationId);
        } catch (Exception e) {
            System.err.println("Failed to create admin: " + e.getMessage());
            System.exit(1);
        } finally {
            ShutdownManager.shutdown();
        }
    }
}
