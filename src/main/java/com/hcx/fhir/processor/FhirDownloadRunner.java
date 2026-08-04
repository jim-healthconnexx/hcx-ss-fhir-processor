package com.hcx.fhir.processor;

import com.hcx.fhir.processor.config.AwsS3Properties;
import com.hcx.fhir.processor.config.SecretsProperties;
import com.hcx.fhir.processor.model.DbCredentials;
import com.hcx.fhir.processor.model.PanelRecord;
import com.hcx.fhir.processor.service.DatabaseService;
import com.hcx.fhir.processor.service.FhirDownloadService;
import com.hcx.fhir.processor.service.KeystoreService;
import com.hcx.fhir.processor.service.PanelService;
import com.hcx.fhir.processor.service.S3FhirOutputService;
import com.hcx.fhir.processor.service.SecretsService;
import com.hcx.fhir.processor.service.SureScriptsFhirClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * HDC-175: ApplicationRunner that drives the FHIR download processing loop.
 *
 * <p>Flow:
 * <ol>
 *   <li>Fetch DB credentials and keystore password from Secrets Manager</li>
 *   <li>Download P12 keystore from S3 and build mTLS SSLContext</li>
 *   <li>Open JDBC connection and query all panels with status SS-Loaded</li>
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
    private final DatabaseService databaseService;
    private final KeystoreService keystoreService;
    private final AwsS3Properties s3Properties;
    private final FhirDownloadService fhirDownloadService;
    private final S3FhirOutputService s3FhirOutputService;
    private final SureScriptsFhirClient fhirClient;
    private final ApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        log.info("HDC-175: Starting FHIR download runner");

        // HDC-175: Step 1 — fetch credentials from Secrets Manager
        DbCredentials dbCreds = secretsService.getDbCredentials(secretsProperties.getDbCredentialsArn());
        // HDC-212: Use getKeystorePassword() to properly handle JSON-wrapped secrets from Secrets Manager.
        String keystorePassword = secretsService.getKeystorePassword(secretsProperties.getKeystorePasswordArn());

        // HDC-175: Step 2 — download keystore from S3 and build mTLS SSLContext
        byte[] p12Bytes = keystoreService.downloadKeystore(
                s3Properties.getKeystoreBucket(), s3Properties.getKeystoreKey());
        SSLContext sslContext = keystoreService.buildSslContext(p12Bytes, keystorePassword);
        HttpClient httpClient = fhirClient.buildHttpClient(sslContext);

        // HDC-175: Step 3 — open DB connection and query panels
        try (Connection conn = databaseService.createConnection(dbCreds)) {
            DSLContext dsl = DSL.using(conn, SQLDialect.POSTGRES);
            PanelService panelService = new PanelService(dsl);

            List<PanelRecord> panels = panelService.fetchSsLoadedPanels();
            log.info("HDC-175: Processing {} SS-Loaded panel(s)", panels.size());

            // HDC-175: Step 4 — process each panel
            for (PanelRecord panel : panels) {
                processPanel(panel, httpClient, panelService);
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
    private void processPanel(PanelRecord panel, HttpClient httpClient, PanelService panelService) {
        log.debug("HDC-175: Processing panelId={} referenceNumber={}", panel.panelId(), panel.referenceNumber());
        try {
            Optional<String> fhirJson = fhirDownloadService.downloadAllPagesForPanel(panel, httpClient);

            if (fhirJson.isPresent()) {
                // HDC-175: Data found — save to S3 and mark panel as received
                s3FhirOutputService.saveToS3(fhirJson.get(), panel);
                panelService.updatePanelStatusFhirReceived(panel.panelId());
                log.info("HDC-175: Panel processed successfully panelId={}", panel.panelId());
            } else {
                // HDC-175: No data — update last_updated to now and move on
                panelService.updatePanelLastUpdated(panel.panelId(), OffsetDateTime.now());
                log.info("HDC-175: No FHIR data for panelId={} — last_updated refreshed", panel.panelId());
            }
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
