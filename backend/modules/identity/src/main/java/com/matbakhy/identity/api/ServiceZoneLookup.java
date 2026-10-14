package com.matbakhy.identity.api;

import java.util.UUID;

/** Implemented by the module that owns service zones; lets identity validate addresses without a module cycle. */
public interface ServiceZoneLookup {

    boolean isActive(UUID zoneId);
}
