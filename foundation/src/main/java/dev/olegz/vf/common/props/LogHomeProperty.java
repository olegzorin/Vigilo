package dev.olegz.vf.common.props;

import ch.qos.logback.core.Context;
import ch.qos.logback.core.spi.PropertyDefiner;
import ch.qos.logback.core.status.Status;
import dev.olegz.vf.common.VigiloEnvironment;

/**
 * Utility class to get the log directory path by Logback.
 * Resolves to {@link VigiloEnvironment#getLogDir()} (i.e. {@code $VF_HOME/logs}), or
 * {@code null} when the home directory is not set so the Logback fallback applies.
 */
public class LogHomeProperty implements PropertyDefiner {
    private Context context;

    @Override
    public String getPropertyValue() {
        return VigiloEnvironment.getLogDir();
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
