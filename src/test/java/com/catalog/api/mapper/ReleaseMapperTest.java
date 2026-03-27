package com.catalog.api.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.catalog.api.dto.ReleaseResponse;
import com.catalog.domain.Application;
import com.catalog.domain.Release;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReleaseMapperTest {

    private final ReleaseMapper mapper = new ReleaseMapper();

    @Test
    void toResponse_mapsAllFields() {
        var app = new Application("app-1", "My App", "Desc", "https://repo.url");
        var releaseDate = Instant.parse("2026-01-15T10:00:00Z");
        var entity = new Release("rel-1", app, 1, 2, 3, "1.2.3",
                "registry.io/app:1.2.3", releaseDate);

        ReleaseResponse response = mapper.toResponse(entity);

        assertThat(response.releaseId()).isEqualTo("rel-1");
        assertThat(response.applicationId()).isEqualTo("app-1");
        assertThat(response.version()).isEqualTo("1.2.3");
        assertThat(response.ociReference()).isEqualTo("registry.io/app:1.2.3");
        assertThat(response.releaseDate()).isEqualTo(releaseDate);
    }

    @Test
    void toResponse_prereleaseVersion() {
        var app = new Application("app-1", "My App", null, null);
        var entity = new Release("rel-2", app, 1, 0, 0, "1.0.0-beta.1",
                "beta", "1~beta.0~0000000001",
                "registry.io/app:1.0.0-beta.1", Instant.now());

        ReleaseResponse response = mapper.toResponse(entity);

        assertThat(response.version()).isEqualTo("1.0.0-beta.1");
        assertThat(response.applicationId()).isEqualTo("app-1");
    }

    @Test
    void toResponse_internalIdsNeverExposed() {
        var app = new Application("ext-app-id", "App", null, null);
        var entity = new Release("ext-rel-id", app, 1, 0, 0, "1.0.0",
                "registry.io/app:1.0.0", Instant.now());

        ReleaseResponse response = mapper.toResponse(entity);

        assertThat(response.releaseId()).isEqualTo("ext-rel-id");
        assertThat(response.applicationId()).isEqualTo("ext-app-id");
    }
}
