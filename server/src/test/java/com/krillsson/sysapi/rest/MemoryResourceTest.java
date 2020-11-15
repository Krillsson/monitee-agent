package com.krillsson.sysapi.rest;

import com.krillsson.sysapi.core.history.MetricsHistoryManager;
import com.krillsson.sysapi.core.metrics.MemoryMetrics;
import com.krillsson.sysapi.util.OffsetDateTimeConverter;
import io.dropwizard.testing.junit.ResourceTestRule;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class MemoryResourceTest {
    private static final MemoryMetrics provider = mock(MemoryMetrics.class);
    private static final MetricsHistoryManager historyManager = mock(MetricsHistoryManager.class);

    @ClassRule
    public static final ResourceTestRule RESOURCES = ResourceTestRule.builder()
            .addResource(new MemoryResource(provider, historyManager))
            .addProvider(OffsetDateTimeConverter.class)
            .build();

    @Test
    public void getMemoryHappyPath() throws Exception {
        com.krillsson.sysapi.core.domain.memory.MemoryLoad memory = mock(com.krillsson.sysapi.core.domain.memory.MemoryLoad.class);
        when(memory.getAvailableBytes()).thenReturn(100L);
        when(memory.getSwapTotalBytes()).thenReturn(100L);
        when(memory.getTotalBytes()).thenReturn(100L);
        when(provider.memoryLoad()).thenReturn(memory);

        final com.krillsson.sysapi.dto.memory.MemoryLoad response = RESOURCES.getJerseyTest().target("/memory")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(com.krillsson.sysapi.dto.memory.MemoryLoad.class);
        assertNotNull(response);
        assertEquals(response.getAvailable(), 100, 0);
    }

    @Test
    public void getMemorySadPath() throws Exception {
        when(provider.memoryLoad()).thenThrow(new RuntimeException("What"));
        final Response response = RESOURCES.getJerseyTest().target("/memory")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get();
        assertEquals(response.getStatus(), 500);
    }

    @After
    public void tearDown() throws Exception {
        reset(provider);
    }
}