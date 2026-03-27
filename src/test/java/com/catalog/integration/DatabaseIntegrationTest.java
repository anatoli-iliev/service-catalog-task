package com.catalog.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.catalog.domain.Application;
import com.catalog.repository.ApplicationRepository;
import com.catalog.repository.ReleaseRepository;
import com.catalog.service.ApplicationService;
import com.catalog.service.ReleaseService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests against a real PostgreSQL instance (from docker-compose).
 * Run {@code docker-compose up -d postgres} before executing these tests.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration"
})
@ActiveProfiles("integration")
class DatabaseIntegrationTest {

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ReleaseService releaseService;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ReleaseRepository releaseRepository;

    @BeforeEach
    void cleanDatabase() {
        releaseRepository.deleteAll();
        applicationRepository.deleteAll();
    }

    @Test
    void upsert_createsNewApplication() {
        Long id = applicationService.upsert("app-new-1", "New App", "Desc", "https://repo.url");

        assertThat(id).isNotNull();
        Application app = applicationRepository.findByExternalApplicationId("app-new-1").orElseThrow();
        assertThat(app.getName()).isEqualTo("New App");
        assertThat(app.getDescription()).isEqualTo("Desc");
    }

    @Test
    void upsert_isIdempotent() {
        applicationService.upsert("app-idem-1", "App", "Desc", "https://repo.url");
        Long secondId = applicationService.upsert("app-idem-1", "App", "Desc", "https://repo.url");

        assertThat(secondId).isNotNull();
        List<Application> all = applicationRepository.findAll().stream()
                .filter(a -> "app-idem-1".equals(a.getExternalApplicationId()))
                .toList();
        assertThat(all).hasSize(1);
    }

    @Test
    void ghostRecord_createdByReleaseUpsert() {
        releaseService.upsert("rel-ghost-1", "app-ghost-1", "1.0.0",
                "registry.io/app:1.0.0", Instant.parse("2026-01-15T10:00:00Z"));

        Application ghost = applicationRepository.findByExternalApplicationId("app-ghost-1").orElseThrow();
        assertThat(ghost.getName()).isNull();
        assertThat(ghost.getDescription()).isNull();
    }

    @Test
    void ghostRecord_promotedByApplicationUpsert() {
        releaseService.upsert("rel-ghost-2", "app-ghost-2", "1.0.0",
                "registry.io/app:1.0.0", Instant.parse("2026-01-15T10:00:00Z"));

        applicationService.upsert("app-ghost-2", "Real App", "Real Desc", "https://real.repo");

        Application promoted = applicationRepository.findByExternalApplicationId("app-ghost-2").orElseThrow();
        assertThat(promoted.getName()).isEqualTo("Real App");
        assertThat(promoted.getDescription()).isEqualTo("Real Desc");
    }

    @Test
    void ghostUpsert_doesNotOverwriteRealData() {
        applicationService.upsert("app-no-overwrite-1", "Real App", "Real Desc", "https://real.repo");

        releaseService.upsert("rel-no-overwrite-1", "app-no-overwrite-1", "2.0.0",
                "registry.io/app:2.0.0", Instant.parse("2026-02-15T10:00:00Z"));

        Application app = applicationRepository.findByExternalApplicationId("app-no-overwrite-1").orElseThrow();
        assertThat(app.getName()).isEqualTo("Real App");
        assertThat(app.getDescription()).isEqualTo("Real Desc");
    }

    @Test
    void releases_sortedBySemVerDescending() {
        String appId = "app-sort-1";
        applicationService.upsert(appId, "Sort App", "Desc", "https://repo.url");

        releaseService.upsert("rel-sort-1", appId, "1.0.0", "reg/a:1.0.0", Instant.now());
        releaseService.upsert("rel-sort-2", appId, "1.9.0", "reg/a:1.9.0", Instant.now());
        releaseService.upsert("rel-sort-3", appId, "1.10.0", "reg/a:1.10.0", Instant.now());
        releaseService.upsert("rel-sort-4", appId, "2.1.0", "reg/a:2.1.0", Instant.now());

        var page = releaseService.findByApplicationExternalId(appId, PageRequest.of(0, 10));
        List<String> versions = page.getContent().stream()
                .map(r -> r.getVersionRaw())
                .toList();

        // String sort would give: 2.1.0, 1.9.0, 1.10.0, 1.0.0 (wrong)
        // Integer sort gives: 2.1.0, 1.10.0, 1.9.0, 1.0.0 (correct)
        assertThat(versions).containsExactly("2.1.0", "1.10.0", "1.9.0", "1.0.0");
    }

    @Test
    void releases_prereleaseSortedBySemVerSpec() {
        String appId = "app-prerelease-1";
        applicationService.upsert(appId, "Prerelease App", "Desc", "https://repo.url");

        // Insert in scrambled order — the full SemVer 2.0.0 precedence chain
        releaseService.upsert("rel-pr-1", appId, "1.0.0-beta.11", "reg/a:1.0.0-beta.11", Instant.now());
        releaseService.upsert("rel-pr-2", appId, "1.0.0", "reg/a:1.0.0", Instant.now());
        releaseService.upsert("rel-pr-3", appId, "1.0.0-alpha", "reg/a:1.0.0-alpha", Instant.now());
        releaseService.upsert("rel-pr-4", appId, "1.0.0-alpha.1", "reg/a:1.0.0-alpha.1", Instant.now());
        releaseService.upsert("rel-pr-5", appId, "1.0.0-rc.1", "reg/a:1.0.0-rc.1", Instant.now());
        releaseService.upsert("rel-pr-6", appId, "1.0.0-beta", "reg/a:1.0.0-beta", Instant.now());
        releaseService.upsert("rel-pr-7", appId, "1.0.0-beta.2", "reg/a:1.0.0-beta.2", Instant.now());
        releaseService.upsert("rel-pr-8", appId, "1.0.0-alpha.beta", "reg/a:1.0.0-alpha.beta", Instant.now());

        var page = releaseService.findByApplicationExternalId(appId, PageRequest.of(0, 20));
        List<String> versions = page.getContent().stream()
                .map(r -> r.getVersionRaw())
                .toList();

        // Expected: highest first (DESC), stable release at top, then pre-releases in descending order
        // SemVer spec: 1.0.0 > 1.0.0-rc.1 > 1.0.0-beta.11 > 1.0.0-beta.2 > 1.0.0-beta
        //              > 1.0.0-alpha.beta > 1.0.0-alpha.1 > 1.0.0-alpha
        assertThat(versions).containsExactly(
                "1.0.0",
                "1.0.0-rc.1",
                "1.0.0-beta.11",
                "1.0.0-beta.2",
                "1.0.0-beta",
                "1.0.0-alpha.beta",
                "1.0.0-alpha.1",
                "1.0.0-alpha");
    }

    @Test
    void releases_mixedStableAndPrereleaseSorted() {
        String appId = "app-mixed-1";
        applicationService.upsert(appId, "Mixed App", "Desc", "https://repo.url");

        releaseService.upsert("rel-mx-1", appId, "2.0.0-rc.1", "reg/a:2.0.0-rc.1", Instant.now());
        releaseService.upsert("rel-mx-2", appId, "1.0.0", "reg/a:1.0.0", Instant.now());
        releaseService.upsert("rel-mx-3", appId, "2.0.0", "reg/a:2.0.0", Instant.now());
        releaseService.upsert("rel-mx-4", appId, "1.1.0-alpha", "reg/a:1.1.0-alpha", Instant.now());

        var page = releaseService.findByApplicationExternalId(appId, PageRequest.of(0, 20));
        List<String> versions = page.getContent().stream()
                .map(r -> r.getVersionRaw())
                .toList();

        assertThat(versions).containsExactly(
                "2.0.0",
                "2.0.0-rc.1",
                "1.1.0-alpha",
                "1.0.0");
    }

    @Test
    void releaseUpsert_isIdempotent() {
        String appId = "app-rel-idem-1";
        applicationService.upsert(appId, "App", "Desc", "https://repo.url");

        releaseService.upsert("rel-idem-1", appId, "1.0.0", "reg/a:1.0.0", Instant.now());
        releaseService.upsert("rel-idem-1", appId, "1.0.0", "reg/a:1.0.0", Instant.now());

        var page = releaseService.findByApplicationExternalId(appId, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
    }
}
