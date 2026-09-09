package dev.olegz.vf.registry.tool;

import dev.olegz.vf.common.ShutdownManager;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.registry.config.DataSourceConfig;
import dev.olegz.vf.registry.config.RegistryConfig;
import dev.olegz.vf.registry.dao.UserDao;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.registry.service.account.UserService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Command-line tool that resets a user's password <em>without</em> knowing the current one,
 * bypassing the REST API.
 * <p>
 * The old-password check only guards {@code authenticate}; {@link UserService#changePassword}
 * simply overwrites the stored hash. There is no admin reset endpoint in the REST API
 * ({@code changePassword} there is gated by {@code requireSelf}), so this out-of-band reset is the
 * intended recovery path when a password is lost. The new password is Argon2id-hashed and run
 * through the same strength checks as the running application.
 * <p>
 * Identify the user by {@code --userId} or {@code --username} (exactly one). Run it via the
 * {@code reset-password} Maven profile (see {@code registry/pom.xml}):
 * <pre>
 *   mvn -q -pl registry -am -Preset-password exec:java -Dexec.args="--username jane"
 *   mvn -q -pl registry -am -Preset-password exec:java -Dexec.args="--userId 57 --password s3cret"
 * </pre>
 * The new password may be supplied with {@code --password}; when omitted it is read from the
 * {@code VF_ADMIN_PASSWORD} environment variable, and failing that prompted for on the console
 * (so it never lands in the shell history).
 */
public final class ResetPasswordTool {

    private ResetPasswordTool() {
    }

    public static void main(String[] args) {
        CliArgs opts = CliArgs.parse(args);

        Integer userIdArg = opts.optInt("userId");
        String username = opts.get("username");
        if (userIdArg == null && (username == null || username.isBlank())) {
            System.err.println("Specify the user to reset with --userId or --username");
            System.exit(1);
        }
        String password = opts.resolvePassword("New password: ");

        PropertyStore.start();
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(RegistryConfig.class, DataSourceConfig.class)) {

            UserService userService = ctx.getBean(UserService.class);
            UserDao userDao = ctx.getBean(UserDao.class);
            TransactionTemplate tx = new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class));

            int userId = tx.execute(_ -> {
                int id;
                if (userIdArg != null) {
                    id = userIdArg;
                } else {
                    User user = userDao.getUserByUsername(username);
                    if (user == null) {
                        throw new IllegalArgumentException("No active user with username '" + username + "'");
                    }
                    id = user.userId;
                }

                if (!userService.changePassword(id, password)) { // hashes + validates the new password
                    throw new IllegalArgumentException("No active user with id " + id);
                }
                return id;
            });

            System.out.println("Reset password for user " + userId);
        } catch (Exception e) {
            System.err.println("Failed to reset password: " + e.getMessage());
            System.exit(1);
        } finally {
            ShutdownManager.shutdown();
        }
    }
}
