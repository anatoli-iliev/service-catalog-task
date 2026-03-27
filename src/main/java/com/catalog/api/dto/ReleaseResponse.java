package com.catalog.api.dto;

import java.time.Instant;

public record ReleaseResponse(
        String releaseId,
        String applicationId,
        String version,
        String ociReference,
        Instant releaseDate
) {
}
