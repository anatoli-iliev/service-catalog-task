package com.catalog.service;

import com.catalog.domain.Application;
import com.catalog.repository.ApplicationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final JdbcTemplate jdbcTemplate;

    public ApplicationService(ApplicationRepository applicationRepository, JdbcTemplate jdbcTemplate) {
        this.applicationRepository = applicationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Long upsert(String externalId, String name, String description, String repositoryUrl) {
        log.debug("Upserting application: externalId={}, isGhost={}", externalId, name == null);
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO applications (external_application_id, name, description, repository_url, created_at, updated_at)
                VALUES (?, ?, ?, ?, now(), now())
                ON CONFLICT (external_application_id) DO UPDATE SET
                    name = COALESCE(EXCLUDED.name, applications.name),
                    description = COALESCE(EXCLUDED.description, applications.description),
                    repository_url = COALESCE(EXCLUDED.repository_url, applications.repository_url),
                    updated_at = now()
                RETURNING id
                """,
                Long.class,
                externalId, name, description, repositoryUrl);
    }

    public Application findByExternalId(String externalId) {
        log.debug("Looking up application: externalId={}", externalId);
        return applicationRepository.findByExternalApplicationId(externalId)
                .orElseThrow(() -> {
                    log.warn("Application not found: externalId={}", externalId);
                    return new ResourceNotFoundException("Application", externalId);
                });
    }

    public Page<Application> findAll(Pageable pageable) {
        log.debug("Listing applications: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        return applicationRepository.findAllByOrderByNameAsc(pageable);
    }
}
