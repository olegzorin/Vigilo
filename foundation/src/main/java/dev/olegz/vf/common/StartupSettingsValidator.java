package dev.olegz.vf.common;

import java.net.InetAddress;
import java.net.UnknownHostException;

import dev.olegz.vf.common.props.PropertyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates and logs settings required by the application at startup.
 */
public final class StartupSettingsValidator {
    private static final Logger logger = LoggerFactory.getLogger(StartupSettingsValidator.class);

    private StartupSettingsValidator() {
    }

    public static void validate() {
        logger.debug(">validate()");
        String localHost = getLocalHost();

        try {
            String homeDir = VigiloEnvironment.getHomeDir();
            if (homeDir != null && !homeDir.isBlank()) {
                logger.info("Home dir is set to " + homeDir + " on " + localHost);
            } else {
                logger.error("Home dir is not set");
            }

            if (VigiloEnvironment.getKek() != null) {
                logger.info("KEK is set on " + localHost);
            } else {
                logger.error("KEK is not set on " + localHost);
            }

            String jdbcPassword = PropertyStore.decrypt("jdbc.password");
            if (jdbcPassword != null && !jdbcPassword.isBlank()) {
                logger.info("JDBC password is set on " + localHost);
            } else {
                logger.error("JDBC password is not set on " + localHost);
            }
        } catch (Exception e) {
            logger.error("Exception in validating settings on " + localHost, e);
        }

        logger.debug("<validate()");
    }

    private static String getLocalHost() {
        try {
            InetAddress ip = InetAddress.getLocalHost();
            return ip.getHostName() + '/' + ip.getHostAddress();
        } catch (UnknownHostException ignore) {
            return null;
        }
    }
}
