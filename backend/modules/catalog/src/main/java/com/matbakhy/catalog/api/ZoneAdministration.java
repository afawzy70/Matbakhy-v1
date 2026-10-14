package com.matbakhy.catalog.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Operating configuration of service zones (delivery fee, active), versioned; used by operations configuration. */
public interface ZoneAdministration {

    record Zone(UUID id, String code, String nameAr, String nameEn, boolean active, long deliveryFeeMinor, long version) {
    }

    List<Zone> zones();

    Optional<Zone> zone(UUID id);

    /** Applies the change if {@code expectedVersion} matches; returns the updated zone, or empty on a version conflict. */
    Optional<Zone> update(UUID id, long expectedVersion, boolean active, long deliveryFeeMinor);
}
