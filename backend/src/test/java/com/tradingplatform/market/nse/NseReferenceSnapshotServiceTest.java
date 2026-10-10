package com.tradingplatform.market.nse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.persistence.NseReferenceSnapshotEntity;
import com.tradingplatform.persistence.NseReferenceSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class NseReferenceSnapshotServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void validatesAndPersistsIndiaVixSnapshotWithRetrievedTime() throws Exception {
        NseReferenceSnapshotRepository repository = mock(NseReferenceSnapshotRepository.class);
        NseReferenceSnapshotService service = new NseReferenceSnapshotService(repository, mapper);
        JsonNode row = mapper.readTree("""
                {
                  "index":"INDIA VIX",
                  "last":13.59,
                  "variation":-1.19,
                  "percentChange":-8.03,
                  "open":14.78,
                  "high":14.78,
                  "low":13.52,
                  "previousClose":14.78,
                  "timestamp":"2020-10-06T15:30:00+05:30"
                }
                """);

        JsonNode response = service.persistIndiaVix(row);

        assertEquals("INDIA VIX", response.path("symbol").asText());
        assertEquals(-1.19, response.path("change").asDouble());
        var captor = org.mockito.ArgumentCaptor.forClass(NseReferenceSnapshotEntity.class);
        verify(repository).save(captor.capture());
        NseReferenceSnapshotEntity saved = captor.getValue();
        assertEquals("INDIA_VIX", saved.datasetKey);
        assertEquals("2020-10-06T10:00:00Z", saved.providerTimestamp.toString());
        assertNotNull(saved.retrievedAt);
        assertTrue(saved.retrievedAt.isAfter(saved.providerTimestamp));
        assertTrue(saved.payloadJson.contains("\"last\":13.59"));
    }

    @Test
    void persistsFiiAndDiiWithOriginalReportDateAndPayload() throws Exception {
        NseReferenceSnapshotRepository repository = mock(NseReferenceSnapshotRepository.class);
        NseReferenceSnapshotService service = new NseReferenceSnapshotService(repository, mapper);
        JsonNode payload = mapper.readTree("""
                {
                  "value": [
                    {"category":"DII","date":"06-Oct-2026","buyValue":"20146.4","sellValue":"15057.48","netValue":"5088.92"},
                    {"category":"FII/FPI","date":"06-Oct-2026","buyValue":"11258.72","sellValue":"14220.02","netValue":"-2961.3"}
                  ],
                  "Count":2
                }
                """);

        assertSame(payload, service.persistFiiDii(payload));

        var captor = org.mockito.ArgumentCaptor.forClass(NseReferenceSnapshotEntity.class);
        verify(repository).save(captor.capture());
        NseReferenceSnapshotEntity saved = captor.getValue();
        assertEquals("FII_DII", saved.datasetKey);
        assertEquals("2026-10-06", saved.providerDate.toString());
        assertNull(saved.providerTimestamp);
        assertNotNull(saved.retrievedAt);
        assertTrue(saved.payloadJson.contains("\"netValue\":\"-2961.3\""));
    }

    @Test
    void rejectsIncompleteFiiDiiResponseWithoutPersisting() throws Exception {
        NseReferenceSnapshotRepository repository = mock(NseReferenceSnapshotRepository.class);
        NseReferenceSnapshotService service = new NseReferenceSnapshotService(repository, mapper);

        assertThrows(ResponseStatusException.class,
                () -> service.persistFiiDii(mapper.readTree("""
                        {"value":[{"category":"DII","date":"06-Oct-2026","buyValue":"1","sellValue":"2","netValue":"-1"}]}
                        """)));

        verify(repository, never()).save(any());
    }

    @Test
    void rejectsInvalidIndiaVixQuoteWithoutPersisting() {
        NseReferenceSnapshotRepository repository = mock(NseReferenceSnapshotRepository.class);
        NseReferenceSnapshotService service = new NseReferenceSnapshotService(repository, mapper);
        JsonNode row = mapper.createObjectNode().put("index", "INDIA VIX").put("last", 0);

        assertThrows(ResponseStatusException.class, () -> service.persistIndiaVix(row));

        verify(repository, never()).save(any());
    }
}
