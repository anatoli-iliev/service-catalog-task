package com.catalog.api.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.catalog.api.dto.ApplicationResponse;
import com.catalog.domain.Application;
import org.junit.jupiter.api.Test;

class ApplicationMapperTest {

    private final ApplicationMapper mapper = new ApplicationMapper();

    @Test
    void toResponse_mapsAllFields() {
        var entity = new Application("app-1", "My App", "A description", "https://github.com/org/app");
        ApplicationResponse response = mapper.toResponse(entity);

        assertThat(response.applicationId()).isEqualTo("app-1");
        assertThat(response.name()).isEqualTo("My App");
        assertThat(response.description()).isEqualTo("A description");
        assertThat(response.repositoryUrl()).isEqualTo("https://github.com/org/app");
    }

    @Test
    void toResponse_ghostRecord_hasNullFields() {
        var ghost = new Application("app-ghost", null, null, null);
        ApplicationResponse response = mapper.toResponse(ghost);

        assertThat(response.applicationId()).isEqualTo("app-ghost");
        assertThat(response.name()).isNull();
        assertThat(response.description()).isNull();
        assertThat(response.repositoryUrl()).isNull();
    }
}
