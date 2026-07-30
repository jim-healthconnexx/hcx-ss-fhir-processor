package com.hcx.fhir.processor.service;

import com.hcx.fhir.processor.config.FhirProperties;
import com.hcx.fhir.processor.model.PanelRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// HDC-175: Orchestrates FHIR data retrieval for a single panel, including pagination.
// Stores all raw page JSON responses in a JSON array — no parsing of clinical data.
@Slf4j
@Service
@RequiredArgsConstructor
public class FhirDownloadService {

    private static final DateTimeFormatter UTC_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final long LOOKBACK_HOURS = 2;

    private final FhirProperties fhirProperties;
    private final SureScriptsFhirClient fhirClient;

    // HDC-175: Downloads all FHIR pages for the given panel.
    // Returns the assembled JSON (array of raw page responses), or empty if no data available.
    public Optional<String> downloadAllPagesForPanel(PanelRecord panel, HttpClient httpClient) {
        String initialUrl = buildInitialUrl(panel);
        log.debug("HDC-175: Starting FHIR download panelId={} url={}", panel.panelId(), initialUrl);

        String firstPage = fhirClient.fetchFhirPage(initialUrl, httpClient);

        if (fhirClient.hasNoResults(firstPage)) {
            log.debug("HDC-175: No FHIR data for panelId={}", panel.panelId());
            return Optional.empty();
        }

        List<String> pages = new ArrayList<>();
        pages.add(firstPage);

        Optional<String> nextUrl = fhirClient.getNextPageUrl(firstPage);
        while (nextUrl.isPresent()) {
            String pageJson = fhirClient.fetchFhirPage(nextUrl.get(), httpClient);
            pages.add(pageJson);
            nextUrl = fhirClient.getNextPageUrl(pageJson);
        }

        log.debug("HDC-175: Collected {} FHIR page(s) for panelId={}", pages.size(), panel.panelId());
        return Optional.of(assemblePages(pages));
    }

    // HDC-175: Builds the initial FHIR query URL for the panel.
    // _lastUpdated=gt{createdOn} and _lastUpdated=lt{lastUpdated+2h or createdOn+2h}
    String buildInitialUrl(PanelRecord panel) {
        OffsetDateTime createdOn = panel.createdOn().withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime lastUpdated = resolveUpperBound(panel);

        return fhirProperties.getBaseUrl() + "/Communication" +
                "?category=panel" +
                "&identifier=" + panel.referenceNumber() +
                "&_lastUpdated=gt" + createdOn.format(UTC_FMT) +
                "&_lastUpdated=lt" + lastUpdated.format(UTC_FMT);
    }

    // HDC-175: Upper bound = lastUpdated + 2h, unless lastUpdated is null or before createdOn,
    // in which case = createdOn + 2h.
    private OffsetDateTime resolveUpperBound(PanelRecord panel) {
        OffsetDateTime createdOn = panel.createdOn().withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime lastUpdated = panel.lastUpdated() != null
                ? panel.lastUpdated().withOffsetSameInstant(ZoneOffset.UTC)
                : null;

        if (lastUpdated == null || lastUpdated.isBefore(createdOn)) {
            return createdOn.plusHours(LOOKBACK_HOURS);
        }
        return lastUpdated.plusHours(LOOKBACK_HOURS);
    }

    // HDC-175: Wraps all raw page JSON bodies in a JSON array.
    // No parsing of FHIR clinical data occurs here.
    private String assemblePages(List<String> pages) {
        if (pages.size() == 1) {
            return pages.get(0);
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < pages.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(pages.get(i));
        }
        sb.append("]");
        return sb.toString();
    }
}
