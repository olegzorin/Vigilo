package dev.olegz.vf.core.domain.lambdarun.input;

import java.util.List;

public record LocationHydrationSnapshot(
    String currentState,
    List<LocationDeviceStateSnapshot> deviceStates,
    List<LocationUserSnapshot> users)
{
}
