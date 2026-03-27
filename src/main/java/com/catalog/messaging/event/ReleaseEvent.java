package com.catalog.messaging.event;

import java.time.Instant;

public record ReleaseEvent(
        String releaseId,
        String applicationId,
        String version,
        String ociReference,
        Instant releaseDate
) {
}
