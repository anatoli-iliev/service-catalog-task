package com.catalog.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.catalog.service.ApplicationService;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class ApplicationEventConsumerTest {

    @Mock
    private ApplicationService applicationService;

    private SimpleMeterRegistry meterRegistry;
    private ApplicationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        consumer = new ApplicationEventConsumer(applicationService, new ObjectMapper(), meterRegistry);
    }

    @Test
    void consume_validEvent_callsUpsertAndIncrementsSuccess() {
        consumer.consume("""
                {"applicationId":"app-1","name":"My App","description":"Desc","repositoryUrl":"https://repo.url"}""");

        verify(applicationService).upsert("app-1", "My App", "Desc", "https://repo.url");
        assertThat(successCount()).isEqualTo(1.0);
        assertThat(failureCount()).isEqualTo(0.0);
    }

    @Test
    void consume_nullApplicationId_skipsAndIncrementsFailure() {
        consumer.consume("""
                {"applicationId":null,"name":"My App","description":"Desc","repositoryUrl":"https://repo.url"}""");

        verifyNoInteractions(applicationService);
        assertThat(failureCount()).isEqualTo(1.0);
        assertThat(successCount()).isEqualTo(0.0);
    }

    @Test
    void consume_blankApplicationId_skipsAndIncrementsFailure() {
        consumer.consume("""
                {"applicationId":"  ","name":"My App","description":"Desc","repositoryUrl":"https://repo.url"}""");

        verifyNoInteractions(applicationService);
        assertThat(failureCount()).isEqualTo(1.0);
    }

    @Test
    void consume_serviceThrowsException_incrementsFailure() {
        doThrow(new RuntimeException("DB down")).when(applicationService)
                .upsert("app-1", "My App", "Desc", "https://repo.url");

        consumer.consume("""
                {"applicationId":"app-1","name":"My App","description":"Desc","repositoryUrl":"https://repo.url"}""");

        assertThat(failureCount()).isEqualTo(1.0);
        assertThat(successCount()).isEqualTo(0.0);
    }

    @Test
    void consume_malformedJson_incrementsFailure() {
        consumer.consume("not valid json {{{");

        verifyNoInteractions(applicationService);
        assertThat(failureCount()).isEqualTo(1.0);
    }

    @Test
    void consume_clearsMdcAfterProcessing() {
        consumer.consume("""
                {"applicationId":"app-1","name":"My App","description":"Desc","repositoryUrl":"https://repo.url"}""");

        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("topic")).isNull();
        assertThat(MDC.get("eventId")).isNull();
    }

    @Test
    void consume_clearsMdcEvenOnFailure() {
        consumer.consume("not valid json");

        assertThat(MDC.get("correlationId")).isNull();
    }

    private double successCount() {
        var counter = meterRegistry.find("catalog.events.processed")
                .tag("topic", "catalog.applications").tag("status", "success").counter();
        return counter != null ? counter.count() : 0.0;
    }

    private double failureCount() {
        var counter = meterRegistry.find("catalog.events.processed")
                .tag("topic", "catalog.applications").tag("status", "failure").counter();
        return counter != null ? counter.count() : 0.0;
    }
}
