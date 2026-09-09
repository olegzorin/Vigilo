package dev.olegz.vf.core.domain.lambdarun.input;

import java.util.Map;

public record LocationDeviceStateSnapshot(
    String deviceUuid,
    Map<String, Object> currentState)
{
}
