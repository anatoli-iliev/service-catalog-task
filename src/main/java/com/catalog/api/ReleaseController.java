package com.catalog.api;

import com.catalog.api.dto.ReleaseResponse;
import com.catalog.api.mapper.ReleaseMapper;
import com.catalog.service.ReleaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/releases")
@Tag(name = "Releases", description = "Software release catalog")
public class ReleaseController {

    private final ReleaseService releaseService;
    private final ReleaseMapper releaseMapper;

    public ReleaseController(ReleaseService releaseService, ReleaseMapper releaseMapper) {
        this.releaseService = releaseService;
        this.releaseMapper = releaseMapper;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get release by external ID")
    public ReleaseResponse getRelease(@PathVariable String id) {
        return releaseMapper.toResponse(releaseService.findByExternalId(id));
    }
}
