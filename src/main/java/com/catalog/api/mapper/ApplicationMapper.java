package com.catalog.api.mapper;

import com.catalog.api.dto.ApplicationResponse;
import com.catalog.domain.Application;
import org.springframework.stereotype.Component;

@Component
public class ApplicationMapper {

    public ApplicationResponse toResponse(Application entity) {
        return new ApplicationResponse(
                entity.getExternalApplicationId(),
                entity.getName(),
                entity.getDescription(),
                entity.getRepositoryUrl());
    }
}
