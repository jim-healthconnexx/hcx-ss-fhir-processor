package com.hcx.fhir.processor;

import com.hcx.fhir.processor.config.AwsS3Properties;
import com.hcx.fhir.processor.config.SecretsProperties;
import com.hcx.fhir.processor.model.PanelRecord;
import com.hcx.fhir.processor.service.FhirDownloadService;
import com.hcx.fhir.processor.service.KeystoreService;
import com.hcx.fhir.processor.service.PanelService;
import com.hcx.fhir.processor.service.S3FhirOutputService;
import com.hcx.fhir.processor.service.SecretsService;
import com.hcx.fhir.processor.service.SureScriptsFhirClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * HDC-175: ApplicationRunner that drives the FHIR download processing loop.
 * HDC-213: DB connection now managed by Spring Boot DataSource auto-configuration.
 *          PanelService is injected as a Spring bean; DatabaseService no longer used.
 *
 * <p>Flow:
 * <ol>
 *   <li>Fetch keystore password from Secrets Manager</li>
 *   <li>Download P12 keystore from S3 and build mTLS SSLContext</li>
 *   <li>Query all panels with status SS-Loaded via injected PanelService</li>
 *   <li>For each panel: fetch FHIR data, page through all results, save to S3,
 *       update panel status to SS-FHIR-Received. If no data: update panel.last_updated.</li>
 *   <li>Shut down application cleanly</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FhirDownloadRunner implements ApplicationRunner {

    private final SecretsService secretsService;
    private final SecretsProperties secretsProperties;
    private final KeystoreService keystoreService;
    private final AwsS3Properties s3Properties;
    private final FhirDownloadService fhirDownloadService;
    private final S3FhirOutputService s3FhirOutputService;
    private final SureScriptsFhirClient fhirClient;
    private final PanelService panelService;
    private final ApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        log.info("HDC-175: Starting FHIR download runner");

        // HDC-175: Step 1 — fetch keystore password from Secrets Manager
        // HDC-213: DB credentials are now injected by DbSecretsEnvironmentPostProcessor before context starts.
        // HDC-212: Use getKeystorePassword() to properly handle JSON-wrapped secrets from Secrets Manager.
        String keystorePassword = secretsService.getKeystorePassword(secretsProperties.getKeystorePasswordArn());

        // HDC-175: Step 2 — download keystore from S3 and build mTLS SSLContext
        byte[] p12Bytes = keystoreService.downloadKeystore(
                s3Properties.getKeystoreBucket(), s3Properties.getKeystoreKey());

        // HDC-214: Optionally load extra CA bundle (e.g. staging .p7b) to extend the truststore.
        byte[] extraCaBytes = null;
        if (s3Properties.getTruststoreKey() != null && !s3Properties.getTruststoreKey().isBlank()) {
            log.info("HDC-214: Loading extra CA bundle from s3://{}/{}", s3Properties.getKeystoreBucket(), s3Properties.getTruststoreKey());
            extraCaBytes = keystoreService.downloadKeystore(s3Properties.getKeystoreBucket(), s3Properties.getTruststoreKey());
        }

        SSLContext sslContext = keystoreService.buildSslContext(p12Bytes, keystorePassword, extraCaBytes);
        HttpClient httpClient = fhirClient.buildHttpClient(sslContext);

        // HDC-175: Step 3 — query panels and process
        try {
            List<PanelRecord> panels = panelService.fetchSsLoadedPanels();
            log.info("HDC-175: Processing {} SS-Loaded panel(s)", panels.size());

            // HDC-175: Step 4 — process each panel
            for (PanelRecord panel : panels) {
                processPanel(panel, httpClient);
            }
        } catch (Exception e) {
            log.error("HDC-175: Fatal error during FHIR download run", e);
            exitApplication(1);
            return;
        }

        log.info("HDC-175: FHIR download runner complete");
        exitApplication(0);
    }

    // HDC-175: Processes a single panel — fetches FHIR data, stores in S3, updates DB status.
    // HDC-227: Compute nextLastUpdated before the FHIR call; always write it back regardless of data found.
    private void processPanel(PanelRecord panel, HttpClient httpClient) {
        log.debug("HDC-175: Processing panelId={} referenceNumber={}", panel.panelId(), panel.referenceNumber());
        try {
            // HDC-227: Capture the upper-bound before the call — this becomes the new panel.last_updated.
            OffsetDateTime nextLastUpdated = fhirDownloadService.computeNextLastUpdated(panel);

            Optional<String> fhirJson = fhirDownloadService.downloadAllPagesForPanel(panel, httpClient);

            if (fhirJson.isPresent()) {
                // HDC-175: Data found — save to S3 and mark panel as received
                s3FhirOutputService.saveToS3(fhirJson.get(), panel);
                panelService.updatePanelStatusFhirReceived(panel.panelId());
                log.info("HDC-175: Panel processed successfully panelId={}", panel.panelId());
            } else {
                log.info("HDC-227: No FHIR data for panelId={}", panel.panelId());
            }

            // HDC-227: Always advance last_updated to the upper bound of the window just queried.
            panelService.updatePanelLastUpdated(panel.panelId(), nextLastUpdated);
            log.debug("HDC-227: Updated last_updated panelId={} nextLastUpdated={}", panel.panelId(), nextLastUpdated);
        } catch (Exception e) {
            log.error("HDC-175: Error processing panelId={} — skipping panel", panel.panelId(), e);
        }
    }

    // HDC-175: Fires Spring shutdown hooks then forces JVM exit.
    private void exitApplication(int code) {
        int exitCode = SpringApplication.exit(applicationContext, () -> code);
        System.exit(exitCode);
    }
}

