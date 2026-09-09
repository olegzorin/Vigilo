package dev.olegz.vf.common.props;

import java.nio.file.Path;

import ch.qos.logback.core.Context;
import ch.qos.logback.core.spi.PropertyDefiner;
import ch.qos.logback.core.status.Status;
import dev.olegz.vf.common.VigiloEnvironment;

/**
 * Utility class to get the configuration directory path by Logback.
 * Resolves to {@link VigiloEnvironment#getConfigDir()} (i.e. {@code $VF_HOME/config}).
 */
public class ConfigHomeProperty implements PropertyDefiner {
    private Context context;

    @Override
    public String getPropertyValue() {
        Path configDir = VigiloEnvironment.getConfigDir();
        return configDir == null ? null : configDir.toString();
    }

    @Override
    public void addError(String arg0) {
    }

    @Override
    public void addError(String arg0, Throwable arg1) {
    }

    @Override
    public void addInfo(String arg0) {
    }

    @Override
    public void addInfo(String arg0, Throwable arg1) {
    }

    @Override
    public void addStatus(Status arg0) {
    }

    @Override
    public void addWarn(String arg0) {
    }

    @Override
    public void addWarn(String arg0, Throwable arg1) {
    }

    @Override
    public Context getContext() {
        return context;
    }

    @Override
    public void setContext(Context context) {
        this.context = context;
    }
}
