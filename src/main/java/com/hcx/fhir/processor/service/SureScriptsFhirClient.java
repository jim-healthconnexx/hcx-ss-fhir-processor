package com.hcx.fhir.processor.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// HDC-175: Makes mTLS HTTPS calls to the SureScripts FHIR API and handles pagination.
@Slf4j
@Service
public class SureScriptsFhirClient {

    // HDC-175: Pattern to extract the "next" link URL from the Bundle JSON.
    // Looks for {"relation":"next","url":"<URL>"} in any field order.
    private static final Pattern NEXT_URL_PATTERN = Pattern.compile(
            "\\{[^}]*\"relation\"\\s*:\\s*\"next\"[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"[^}]*\\}" +
            "|\\{[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"[^}]*\"relation\"\\s*:\\s*\"next\"[^}]*\\}",
            Pattern.DOTALL
    );

    // HDC-175: Builds a Java HttpClient with the mTLS SSLContext.
    public HttpClient buildHttpClient(SSLContext sslContext) {
        return HttpClient.newBuilder()
                .sslContext(sslContext)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    // HDC-175: Performs a GET request to the given FHIR URL and returns the raw JSON response body.
    // HDC-215: senderUid is sent as X-SENDER-UID header; qualifier is fixed at 10 per MHP IG 1.1.1 s7.2.2.
    public String fetchFhirPage(String url, HttpClient httpClient, String senderUid) {
        log.debug("HDC-175: Fetching FHIR page url={}", url);
        if (senderUid == null || senderUid.isBlank()) {
            log.error("HDC-215: senderUid is null or blank — cannot send X-SENDER-UID header url={}", url);
            throw new RuntimeException("HDC-215: senderUid missing for FHIR request");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/fhir+json")
                    .header("Content-Type", "application/fhir+json; charset=UTF-8")
                    .header("X-SENDER-UID", senderUid)
                    .header("X-SENDER-UID-QUALIFIER", "10")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            log.debug("HDC-175: FHIR page response status={} url={}", response.statusCode(), url);

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                // HDC-215: Log response body so the SureScripts error payload is visible in CloudWatch.
                log.error("HDC-175: FHIR API returned non-2xx status={} url={} body={}", response.statusCode(), url, responseBody);
                String truncatedBody = responseBody != null && responseBody.length() > 500
                        ? responseBody.substring(0, 500) + "…"
                        : responseBody;
                throw new RuntimeException("HDC-175: FHIR API error status=" + response.statusCode() + " body=" + truncatedBody);
            }

            return responseBody;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("HDC-175: Failed to fetch FHIR page url={}", url, e);
            throw new RuntimeException("HDC-175: Failed to fetch FHIR page", e);
        }
    }

    // HDC-175: Extracts the "next" page URL from a FHIR Bundle JSON response.
    // Returns empty if no next link is present (last page).
    public Optional<String> getNextPageUrl(String bundleJson) {
        Matcher m = NEXT_URL_PATTERN.matcher(bundleJson);
        if (m.find()) {
            String url = m.group(1) != null ? m.group(1) : m.group(2);
            log.debug("HDC-175: Found next page url={}", url);
            return Optional.of(url);
        }
        log.debug("HDC-175: No next page link found — last page reached");
        return Optional.empty();
    }

    // HDC-175: Returns true if the FHIR Bundle JSON has no results (total = 0 or no entries).
    public boolean hasNoResults(String bundleJson) {
        return bundleJson.contains("\"total\":0") || bundleJson.contains("\"total\": 0");
    }
}
