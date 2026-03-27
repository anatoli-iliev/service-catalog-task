package com.catalog.service;

import com.catalog.domain.Release;
import com.catalog.repository.ReleaseRepository;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ReleaseService {

    private static final Pattern SEMVER_PATTERN = Pattern.compile(
            "^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^\\d+$");

    private final ReleaseRepository releaseRepository;
    private final ApplicationService applicationService;
    private final JdbcTemplate jdbcTemplate;

    public ReleaseService(ReleaseRepository releaseRepository,
                          ApplicationService applicationService,
                          JdbcTemplate jdbcTemplate) {
        this.releaseRepository = releaseRepository;
        this.applicationService = applicationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void upsert(String externalReleaseId, String externalApplicationId,
                       String version, String ociReference, Instant releaseDate) {
        log.debug("Upserting release: externalId={}, applicationId={}, version={}",
                externalReleaseId, externalApplicationId, version);
        Long applicationId = applicationService.upsert(externalApplicationId, null, null, null);

        SemVer semver = parseSemVer(version);

        jdbcTemplate.update(
                """
                INSERT INTO releases (external_release_id, application_id, version_major, version_minor, version_patch,
                                      version_prerelease, version_prerelease_sort_key,
                                      version_raw, oci_reference, release_date, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                ON CONFLICT (external_release_id) DO UPDATE SET
                    version_major = EXCLUDED.version_major,
                    version_minor = EXCLUDED.version_minor,
                    version_patch = EXCLUDED.version_patch,
                    version_prerelease = EXCLUDED.version_prerelease,
                    version_prerelease_sort_key = EXCLUDED.version_prerelease_sort_key,
                    version_raw = EXCLUDED.version_raw,
                    oci_reference = EXCLUDED.oci_reference,
                    release_date = EXCLUDED.release_date,
                    updated_at = now()
                """,
                externalReleaseId, applicationId,
                semver.major(), semver.minor(), semver.patch(),
                semver.prerelease(), semver.prereleaseSortKey(),
                version, ociReference, releaseDate.atOffset(java.time.ZoneOffset.UTC));
    }

    public Release findByExternalId(String externalId) {
        log.debug("Looking up release: externalId={}", externalId);
        return releaseRepository.findByExternalReleaseId(externalId)
                .orElseThrow(() -> {
                    log.warn("Release not found: externalId={}", externalId);
                    return new ResourceNotFoundException("Release", externalId);
                });
    }

    public Page<Release> findByApplicationExternalId(String externalApplicationId, Pageable pageable) {
        log.debug("Listing releases: applicationId={}, page={}, size={}",
                externalApplicationId, pageable.getPageNumber(), pageable.getPageSize());
        return releaseRepository.findByApplicationExternalId(externalApplicationId, pageable);
    }

    record SemVer(int major, int minor, int patch, String prerelease, String prereleaseSortKey) {}

    static SemVer parseSemVer(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Version must not be blank");
        }
        Matcher matcher = SEMVER_PATTERN.matcher(version.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid SemVer format: " + version);
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));
        String prerelease = matcher.group(4);
        String sortKey = prerelease != null ? buildPrereleaseSortKey(prerelease) : null;

        return new SemVer(major, minor, patch, prerelease, sortKey);
    }

    /**
     * Builds a sort key that produces correct SemVer 2.0.0 precedence when compared lexicographically.
     * <p>
     * Rules per spec:
     * <ul>
     *   <li>Numeric identifiers are compared as integers: 2 < 11</li>
     *   <li>Alphanumeric identifiers are compared as strings: "alpha" < "beta"</li>
     *   <li>Numeric identifiers always have lower precedence than alphanumeric: 1 < "alpha"</li>
     *   <li>A shorter set of identifiers has lower precedence if all preceding are equal</li>
     * </ul>
     * <p>
     * Encoding: numeric → "0~" + zero-padded(10), alphanumeric → "1~" + value.
     * Since '0' < '1' lexicographically, numeric sorts before alphanumeric.
     */
    static String buildPrereleaseSortKey(String prerelease) {
        String[] identifiers = prerelease.split("\\.");
        var sb = new StringBuilder();
        for (int i = 0; i < identifiers.length; i++) {
            if (i > 0) sb.append('.');
            String id = identifiers[i];
            if (NUMERIC_PATTERN.matcher(id).matches()) {
                sb.append("0~").append(String.format("%010d", Long.parseLong(id)));
            } else {
                sb.append("1~").append(id);
            }
        }
        return sb.toString();
    }
}
