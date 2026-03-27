package com.catalog.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.catalog.api.mapper.ReleaseMapper;
import com.catalog.domain.Application;
import com.catalog.domain.Release;
import com.catalog.service.ReleaseService;
import com.catalog.service.ResourceNotFoundException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReleaseController.class)
class ReleaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReleaseService releaseService;

    @MockitoBean
    private ReleaseMapper releaseMapper;

    @Test
    void getRelease_returnsRelease() throws Exception {
        var release = new Release("rel-1", new Application("app-1", "My App", null, null),
                1, 0, 0, "1.0.0", "registry.io/app:1.0.0", Instant.parse("2026-01-15T10:00:00Z"));
        var response = new com.catalog.api.dto.ReleaseResponse(
                "rel-1", "app-1", "1.0.0", "registry.io/app:1.0.0", Instant.parse("2026-01-15T10:00:00Z"));

        when(releaseService.findByExternalId("rel-1")).thenReturn(release);
        when(releaseMapper.toResponse(release)).thenReturn(response);

        mockMvc.perform(get("/api/v1/releases/rel-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseId").value("rel-1"))
                .andExpect(jsonPath("$.version").value("1.0.0"));
    }

    @Test
    void getRelease_notFound() throws Exception {
        when(releaseService.findByExternalId("unknown"))
                .thenThrow(new ResourceNotFoundException("Release", "unknown"));

        mockMvc.perform(get("/api/v1/releases/unknown"))
                .andExpect(status().isNotFound());
    }
}
