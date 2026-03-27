package com.catalog.api.mapper;

import com.catalog.api.dto.ReleaseResponse;
import com.catalog.domain.Release;
import org.springframework.stereotype.Component;

@Component
public class ReleaseMapper {

    public ReleaseResponse toResponse(Release entity) {
        return new ReleaseResponse(
                entity.getExternalReleaseId(),
                entity.getApplication().getExternalApplicationId(),
                entity.getVersionRaw(),
                entity.getOciReference(),
                entity.getReleaseDate());
    }
}
