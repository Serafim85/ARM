package com.networkscanner.backend.integration.event;

import com.networkscanner.backend.integration.dto.ProbeAvailabilityUpdate;

public record WislaAvailabilityChangedEvent(ProbeAvailabilityUpdate payload) {
}
