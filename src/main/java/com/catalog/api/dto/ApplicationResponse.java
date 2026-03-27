package com.catalog.api.dto;

public record ApplicationResponse(
        String applicationId,
        String name,
        String description,
        String repositoryUrl
) {
}
