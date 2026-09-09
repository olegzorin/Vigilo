package dev.olegz.vf.core.service.lambda;

import dev.olegz.vf.core.domain.lambdarun.input.TriggerEventData;
import dev.olegz.vf.core.event.ResetEvent;
import dev.olegz.vf.core.event.ScheduledEvent;
import dev.olegz.vf.core.event.TriggerEvent;

public interface TriggerEventDataService {
    TriggerEventData hydrate(ResetEvent event);

    TriggerEventData hydrate(ScheduledEvent event);

    TriggerEventData hydrate(TriggerEvent event);
}
