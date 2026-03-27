package com.catalog.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.catalog.service.ReleaseService;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class ReleaseEventConsumerTest {

    @Mock
    private ReleaseService releaseService;

    private SimpleMeterRegistry meterRegistry;
    private ReleaseEventConsumer consumer;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        consumer = new ReleaseEventConsumer(releaseService, new ObjectMapper(), meterRegistry);
    }

    @Test
    void consume_validEvent_callsUpsertAndIncrementsSuccess() {
        var releaseDate = Instant.parse("2026-01-15T10:00:00Z");

        consumer.consume("""
                {"releaseId":"rel-1","applicationId":"app-1","version":"1.0.0","ociReference":"registry.io/app:1.0.0","releaseDate":"2026-01-15T10:00:00Z"}""");

        verify(releaseService).upsert("rel-1", "app-1", "1.0.0", "registry.io/app:1.0.0", releaseDate);
        assertThat(successCount()).isEqualTo(1.0);
        assertThat(failureCount()).isEqualTo(0.0);
    }

    @Test
    void consume_nullReleaseId_skipsAndIncrementsFailure() {
        consumer.consume("""
                {"releaseId":null,"applicationId":"app-1","version":"1.0.0","ociReference":"registry.io/app:1.0.0","releaseDate":"2026-01-15T10:00:00Z"}""");

        verifyNoInteractions(releaseService);
        assertThat(failureCount()).isEqualTo(1.0);
    }

    @Test
    void consume_nullApplicationId_skipsAndIncrementsFailure() {
        consumer.consume("""
                {"releaseId":"rel-1","applicationId":null,"version":"1.0.0","ociReference":"registry.io/app:1.0.0","releaseDate":"2026-01-15T10:00:00Z"}""");

        verifyNoInteractions(releaseService);
        assertThat(failureCount()).isEqualTo(1.0);
    }

    @Test
    void consume_blankReleaseId_skipsAndIncrementsFailure() {
        consumer.consume("""
                {"releaseId":"  ","applicationId":"app-1","version":"1.0.0","ociReference":"registry.io/app:1.0.0","releaseDate":"2026-01-15T10:00:00Z"}""");

        verifyNoInteractions(releaseService);
        assertThat(failureCount()).isEqualTo(1.0);
    }

    @Test
    void consume_serviceThrowsException_incrementsFailure() {
        var releaseDate = Instant.parse("2026-01-15T10:00:00Z");
        doThrow(new RuntimeException("DB down")).when(releaseService)
                .upsert("rel-1", "app-1", "1.0.0", "registry.io/app:1.0.0", releaseDate);

        consumer.consume("""
                {"releaseId":"rel-1","applicationId":"app-1","version":"1.0.0","ociReference":"registry.io/app:1.0.0","releaseDate":"2026-01-15T10:00:00Z"}""");

        assertThat(failureCount()).isEqualTo(1.0);
        assertThat(successCount()).isEqualTo(0.0);
    }

    @Test
    void consume_malformedJson_incrementsFailure() {
        consumer.consume("not valid json");

        verifyNoInteractions(releaseService);
        assertThat(failureCount()).isEqualTo(1.0);
    }

    @Test
    void consume_clearsMdcAfterProcessing() {
        consumer.consume("""
                {"releaseId":"rel-1","applicationId":"app-1","version":"1.0.0","ociReference":"registry.io/app:1.0.0","releaseDate":"2026-01-15T10:00:00Z"}""");

        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("topic")).isNull();
        assertThat(MDC.get("eventId")).isNull();
    }

    private double successCount() {
        var counter = meterRegistry.find("catalog.events.processed")
                .tag("topic", "catalog.releases").tag("status", "success").counter();
        return counter != null ? counter.count() : 0.0;
    }

    private double failureCount() {
        var counter = meterRegistry.find("catalog.events.processed")
                .tag("topic", "catalog.releases").tag("status", "failure").counter();
        return counter != null ? counter.count() : 0.0;
    }
}
