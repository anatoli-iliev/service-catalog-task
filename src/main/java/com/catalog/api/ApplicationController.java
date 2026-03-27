package com.catalog.api;

import com.catalog.api.dto.ApplicationResponse;
import com.catalog.api.dto.ReleaseResponse;
import com.catalog.api.mapper.ApplicationMapper;
import com.catalog.api.mapper.ReleaseMapper;
import com.catalog.service.ApplicationService;
import com.catalog.service.ReleaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "Applications", description = "Software application catalog")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final ReleaseService releaseService;
    private final ApplicationMapper applicationMapper;
    private final ReleaseMapper releaseMapper;

    public ApplicationController(ApplicationService applicationService,
                                 ReleaseService releaseService,
                                 ApplicationMapper applicationMapper,
                                 ReleaseMapper releaseMapper) {
        this.applicationService = applicationService;
        this.releaseService = releaseService;
        this.applicationMapper = applicationMapper;
        this.releaseMapper = releaseMapper;
    }

    private static final int MAX_PAGE_SIZE = 100;

    @GetMapping
    @Operation(summary = "List all applications", description = "Paginated, sorted alphabetically by name")
    public Page<ApplicationResponse> listApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return applicationService.findAll(PageRequest.of(Math.max(0, page), clampSize(size)))
                .map(applicationMapper::toResponse);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get application by external ID")
    public ApplicationResponse getApplication(@PathVariable String id) {
        return applicationMapper.toResponse(applicationService.findByExternalId(id));
    }

    @GetMapping("/{id}/releases")
    @Operation(summary = "List releases for an application", description = "Paginated, sorted by SemVer descending")
    public Page<ReleaseResponse> listReleases(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return releaseService.findByApplicationExternalId(id, PageRequest.of(Math.max(0, page), clampSize(size)))
                .map(releaseMapper::toResponse);
    }

    private static int clampSize(int size) {
        return Math.clamp(size, 1, MAX_PAGE_SIZE);
    }
}
