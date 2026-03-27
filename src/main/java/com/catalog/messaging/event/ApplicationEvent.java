package com.catalog.messaging.event;

public record ApplicationEvent(
        String applicationId,
        String name,
        String description,
        String repositoryUrl
) {
}
