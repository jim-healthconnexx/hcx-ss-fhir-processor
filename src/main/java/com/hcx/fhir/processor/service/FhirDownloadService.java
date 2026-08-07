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

        String firstPage = fhirClient.fetchFhirPage(initialUrl, httpClient, panel.senderUid());

        if (fhirClient.hasNoResults(firstPage)) {
            log.debug("HDC-175: No FHIR data for panelId={}", panel.panelId());
            return Optional.empty();
        }

        List<String> pages = new ArrayList<>();
        pages.add(firstPage);

        Optional<String> nextUrl = fhirClient.getNextPageUrl(firstPage);
        while (nextUrl.isPresent()) {
            String pageJson = fhirClient.fetchFhirPage(nextUrl.get(), httpClient, panel.senderUid());
            pages.add(pageJson);
            nextUrl = fhirClient.getNextPageUrl(pageJson);
        }

        log.debug("HDC-175: Collected {} FHIR page(s) for panelId={}", pages.size(), panel.panelId());
        return Optional.of(assemblePages(pages));
    }

    // HDC-175: Builds the initial FHIR query URL for the panel.
    // HDC-227: When last_updated IS NULL: gt{createdOn} lt{currentDateTime}
    //          When last_updated IS NOT NULL: gt{lastUpdated} lt{lastUpdated+2h}
    // HDC-218: Added _include and _include:iterate parameters to fetch related resources.
    String buildInitialUrl(PanelRecord panel) {
        OffsetDateTime lowerBound = resolveLowerBound(panel);
        OffsetDateTime upperBound = computeNextLastUpdated(panel);

        return fhirProperties.getBaseUrl() + "/Communication" +
                "?category=panel" +
                "&identifier=" + panel.referenceNumber() +
                "&_lastUpdated=gt" + lowerBound.format(UTC_FMT) +
                "&_lastUpdated=lt" + upperBound.format(UTC_FMT) +
                "&_include=Communication:based-on" +
                "&_include:iterate=Communication:subject" +
                "&_include:iterate=MedicationRequest:requester" +
                "&_include:iterate=MedicationRequest:medication" +
                "&_include:iterate=MedicationDispense:performer" +
                "&_include:iterate=MedicationDispense:prescription" +
                "&_include:iterate=MedicationRequest:intended-performer";
    }

    // HDC-227: Lower bound for _lastUpdated query.
    // When last_updated IS NULL: use panel.created_on.
    // When last_updated IS NOT NULL: use panel.last_updated.
    private OffsetDateTime resolveLowerBound(PanelRecord panel) {
        if (panel.lastUpdated() == null) {
            return panel.createdOn().withOffsetSameInstant(ZoneOffset.UTC);
        }
        return panel.lastUpdated().withOffsetSameInstant(ZoneOffset.UTC);
    }

    // HDC-227: Computes the upper bound for _lastUpdated query and the value to write back to panel.last_updated.
    // When last_updated IS NULL: currentDateTime (captured once per panel run).
    // When last_updated IS NOT NULL: panel.last_updated + 2h.
    public OffsetDateTime computeNextLastUpdated(PanelRecord panel) {
        if (panel.lastUpdated() == null) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        return panel.lastUpdated().withOffsetSameInstant(ZoneOffset.UTC).plusHours(LOOKBACK_HOURS);
    }

    // HDC-175: Upper bound = lastUpdated + 2h, unless lastUpdated is null or before createdOn,
    // in which case = createdOn + 2h.
    // HDC-227: Replaced by resolveLowerBound() and computeNextLastUpdated().
    @Deprecated
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
