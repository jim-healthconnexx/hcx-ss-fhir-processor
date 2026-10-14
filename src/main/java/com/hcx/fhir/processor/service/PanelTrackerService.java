package com.hcx.fhir.processor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

// HDC-308: Writes audit rows to healthdata.panel_tracker so external monitoring can track panel
// progress/errors as this app processes each panel. Schema is set via JDBC URL currentSchema=healthdata
// in the DB secret's dbname value; no schema prefix in jOOQ calls (same convention as PanelService).
@Slf4j
@Component
@RequiredArgsConstructor
public class PanelTrackerService {

    private final DSLContext dsl;
    private final LogStreamService logStreamService;

    @Value("${spring.application.name}")
    private String appName;

    /**
     * Inserts one row into healthdata.panel_tracker capturing the panel's current outcome
     * (success, retry, or terminal error) along with the app name, retry_count, created_on,
     * and the resolved CloudWatch log_stream for this run.
     */
    public void recordTracker(int panelId, String status, int retryCount) {
        String logStream = logStreamService.resolveLogStream();
        log.debug("HDC-308: Writing panel_tracker panelId={} status={} retryCount={} logStream={}",
                panelId, status, retryCount, logStream);
        dsl.insertInto(table(name("panel_tracker")))
                .set(field(name("panel_id")), panelId)
                .set(field(name("status")), status)
                .set(field(name("app")), appName)
                .set(field(name("retry_count")), retryCount)
                .set(field(name("created_on")), OffsetDateTime.now())
                .set(field(name("log_stream")), logStream)
                .execute();
        log.debug("HDC-308: Wrote panel_tracker panelId={} status={}", panelId, status);
    }
}
