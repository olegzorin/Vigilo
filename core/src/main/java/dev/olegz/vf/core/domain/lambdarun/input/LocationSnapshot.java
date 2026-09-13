package dev.olegz.vf.core.domain.lambdarun.input;

import dev.olegz.vf.registry.domain.account.LocationType;
public class LocationSnapshot {
    public LocationType locationType;
    public String name;
    public String timezone;
    public String country;
    public String city;
    public String postalCode;
    public String currentState;

    public static LocationSnapshot from(LocationMetadataSnapshot metadata, String currentState) {
        if (metadata == null) return null;

        LocationSnapshot snapshot = new LocationSnapshot();
        snapshot.locationType = metadata.locationType;
        snapshot.name = metadata.name;
        snapshot.timezone = metadata.timezone;
        snapshot.country = metadata.country;
        snapshot.city = metadata.city;
        snapshot.postalCode = metadata.postalCode;
        snapshot.currentState = currentState;
        return snapshot;
    }
}
