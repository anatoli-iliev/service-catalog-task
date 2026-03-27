package com.catalog.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.catalog.api.mapper.ApplicationMapper;
import com.catalog.api.mapper.ReleaseMapper;
import com.catalog.domain.Application;
import com.catalog.domain.Release;
import com.catalog.service.ApplicationService;
import com.catalog.service.ReleaseService;
import com.catalog.service.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ApplicationController.class)
class ApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationService applicationService;

    @MockitoBean
    private ReleaseService releaseService;

    @MockitoBean
    private ApplicationMapper applicationMapper;

    @MockitoBean
    private ReleaseMapper releaseMapper;

    @Test
    void listApplications_returnsPage() throws Exception {
        var app = new Application("app-1", "My App", "Description", "https://github.com/org/app");
        var response = new com.catalog.api.dto.ApplicationResponse("app-1", "My App", "Description", "https://github.com/org/app");

        when(applicationService.findAll(any())).thenReturn(new PageImpl<>(List.of(app)));
        when(applicationMapper.toResponse(app)).thenReturn(response);

        mockMvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].applicationId").value("app-1"))
                .andExpect(jsonPath("$.content[0].name").value("My App"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getApplication_returnsApp() throws Exception {
        var app = new Application("app-1", "My App", "Description", "https://github.com/org/app");
        var response = new com.catalog.api.dto.ApplicationResponse("app-1", "My App", "Description", "https://github.com/org/app");

        when(applicationService.findByExternalId("app-1")).thenReturn(app);
        when(applicationMapper.toResponse(app)).thenReturn(response);

        mockMvc.perform(get("/api/v1/applications/app-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value("app-1"));
    }

    @Test
    void getApplication_notFound() throws Exception {
        when(applicationService.findByExternalId("unknown"))
                .thenThrow(new ResourceNotFoundException("Application", "unknown"));

        mockMvc.perform(get("/api/v1/applications/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Application not found: unknown"));
    }

    @Test
    void listReleases_returnsPage() throws Exception {
        var release = new Release("rel-1", new Application("app-1", "My App", null, null),
                1, 0, 0, "1.0.0", "registry.io/app:1.0.0", Instant.parse("2026-01-15T10:00:00Z"));
        var response = new com.catalog.api.dto.ReleaseResponse(
                "rel-1", "app-1", "1.0.0", "registry.io/app:1.0.0", Instant.parse("2026-01-15T10:00:00Z"));

        when(releaseService.findByApplicationExternalId(eq("app-1"), any()))
                .thenReturn(new PageImpl<>(List.of(release)));
        when(releaseMapper.toResponse(release)).thenReturn(response);

        mockMvc.perform(get("/api/v1/applications/app-1/releases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].releaseId").value("rel-1"))
                .andExpect(jsonPath("$.content[0].version").value("1.0.0"));
    }
}
