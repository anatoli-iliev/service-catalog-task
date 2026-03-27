package com.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class EntityTest {

    @Test
    void application_constructorAndGetters() {
        var app = new Application("ext-id", "App Name", "Description", "https://repo.url");

        assertThat(app.getId()).isNull();
        assertThat(app.getExternalApplicationId()).isEqualTo("ext-id");
        assertThat(app.getName()).isEqualTo("App Name");
        assertThat(app.getDescription()).isEqualTo("Description");
        assertThat(app.getRepositoryUrl()).isEqualTo("https://repo.url");
        assertThat(app.getCreatedAt()).isNull();
        assertThat(app.getUpdatedAt()).isNull();
    }

    @Test
    void application_lifecycleCallbacks() {
        var app = new Application("ext-id", "App", null, null);

        app.prePersist();
        assertThat(app.getCreatedAt()).isNotNull();
        assertThat(app.getUpdatedAt()).isNotNull();
        assertThat(app.getCreatedAt()).isEqualTo(app.getUpdatedAt());

        var createdAt = app.getCreatedAt();
        app.preUpdate();
        assertThat(app.getCreatedAt()).isEqualTo(createdAt);
        assertThat(app.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }

    @Test
    void release_stableVersionConstructor() {
        var app = new Application("app-1", "App", null, null);
        var releaseDate = Instant.parse("2026-01-15T10:00:00Z");
        var release = new Release("rel-1", app, 1, 2, 3, "1.2.3",
                "registry.io/app:1.2.3", releaseDate);

        assertThat(release.getId()).isNull();
        assertThat(release.getExternalReleaseId()).isEqualTo("rel-1");
        assertThat(release.getApplication()).isSameAs(app);
        assertThat(release.getVersionMajor()).isEqualTo(1);
        assertThat(release.getVersionMinor()).isEqualTo(2);
        assertThat(release.getVersionPatch()).isEqualTo(3);
        assertThat(release.getVersionRaw()).isEqualTo("1.2.3");
        assertThat(release.getVersionPrerelease()).isNull();
        assertThat(release.getVersionPrereleaseSortKey()).isNull();
        assertThat(release.getOciReference()).isEqualTo("registry.io/app:1.2.3");
        assertThat(release.getReleaseDate()).isEqualTo(releaseDate);
    }

    @Test
    void release_prereleaseVersionConstructor() {
        var app = new Application("app-1", "App", null, null);
        var release = new Release("rel-2", app, 1, 0, 0, "1.0.0-beta.1",
                "beta.1", "1~beta.0~0000000001",
                "registry.io/app:1.0.0-beta.1", Instant.now());

        assertThat(release.getVersionPrerelease()).isEqualTo("beta.1");
        assertThat(release.getVersionPrereleaseSortKey()).isEqualTo("1~beta.0~0000000001");
        assertThat(release.getVersionRaw()).isEqualTo("1.0.0-beta.1");
    }

    @Test
    void release_lifecycleCallbacks() {
        var app = new Application("app-1", "App", null, null);
        var release = new Release("rel-1", app, 1, 0, 0, "1.0.0",
                "registry.io/app:1.0.0", Instant.now());

        release.prePersist();
        assertThat(release.getCreatedAt()).isNotNull();
        assertThat(release.getUpdatedAt()).isNotNull();

        var createdAt = release.getCreatedAt();
        release.preUpdate();
        assertThat(release.getCreatedAt()).isEqualTo(createdAt);
        assertThat(release.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }
}
