package dev.olegz.vf.core.domain.lambdabuild;

import java.util.List;
import java.util.Map;

import dev.olegz.vf.common.exception.MissingParameterException;
import dev.olegz.vf.common.exception.WrongParameterValueException;
import dev.olegz.vf.common.schedule.CronExpression;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.core.domain.lambdaversion.VersionBump;
import dev.olegz.vf.core.event.TriggerEvent;

public class LambdaConfig {
    private static final int SUPPORTED_TRIGGERS =
        TriggerEvent.TRIGGER_SCHEDULE |
            TriggerEvent.TRIGGER_LOCATION_EVENT |
            TriggerEvent.TRIGGER_DEVICE_EVENT;

    public VersionBump bump;
    public String whatsnew;
    public int trigger;
    public Map<String, String> schedule;
    public List<String> addresses;

    public void checkTriggers() {
        if ((trigger <= 0) || ((trigger & ~SUPPORTED_TRIGGERS) != 0)) {
            throw new WrongParameterValueException("Wrong trigger value");
        }

        if (checkTrigger(TriggerEvent.TRIGGER_SCHEDULE)) {
            if ((schedule == null) || schedule.isEmpty()) {
                throw new MissingParameterException("Missing schedules");
            }
            if (CollectionOps.anyMatch(schedule.values(), expr -> !CronExpression.isValidExpression(expr))) {
                throw new WrongParameterValueException("Wrong schedule: " + schedule);
            }
        } else {
            schedule = null;
        }
    }

    private boolean checkTrigger(int trigger) {
        return (this.trigger & trigger) != 0;
    }

    @Override
    public String toString() {
        return "{versionBump=" + bump +
            ", trigger=" + trigger +
            ", whatsnew='" + whatsnew + "'" +
            ", schedule=" + schedule +
            ", addresses=" + addresses + '}';
    }
}
