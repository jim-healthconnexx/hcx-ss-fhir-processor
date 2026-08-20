package com.hcx.fhir.processor.service;

import com.hcx.fhir.processor.model.PanelRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

// HDC-213: Now a Spring @Component — DSLContext injected by Spring Boot jOOQ auto-configuration.
// Schema is set via JDBC URL currentSchema=healthdata in the DB secret's dbname value; no schema prefix in jOOQ calls.
@Slf4j
@Component
@RequiredArgsConstructor
public class PanelService {

    static final String STATUS_SS_LOADED = "SS-Loaded";
    static final String STATUS_SS_FHIR_RECEIVED = "SS-FHIR-Received";
    // HDC-261: Status set when an exception occurs during per-page FHIR processing.
    static final String STATUS_ERROR_FHIR_PROCESSING = "ERROR-FHIR-PROCESSING";

    private final DSLContext dsl;

    // HDC-175: Returns all panels with status = 'SS-Loaded'.
    // HDC-215: JOINs product table to extract HDR.SenderID from file_config JSON for X-SENDER-UID header.
    public List<PanelRecord> fetchSsLoadedPanels() {
        log.debug("HDC-175: Querying panel table for status={}", STATUS_SS_LOADED);
        List<PanelRecord> panels = dsl.select(
                        field(name("p", "panel_id")),
                        field(name("p", "reference_number")),
                        field(name("p", "status")),
                        field(name("p", "created_on")),
                        field(name("p", "last_updated")),
                        field(name("p", "data_source")),
                        field(name("p", "sent_request_filename")),
                        field("({0}::jsonb->'HDR'->>'SenderID')", String.class, field(name("pr", "file_config"))).as("sender_uid"),
                        field(name("p", "fhir_next_url")))
                .from(table(name("panel")).as("p"))
                .join(table(name("product")).as("pr"))
                .on(field(name("p", "product_id")).eq(field(name("pr", "product_id"))))
                .where(field(name("p", "status")).eq(STATUS_SS_LOADED))
                .fetch(this::toPanelRecord);
        log.debug("HDC-175: Found {} SS-Loaded panel(s)", panels.size());
        return panels;
    }

    // HDC-242: Fetches any senderUid from product.file_config for use in CapabilityStatement auth header.
    // The /metadata endpoint is server-level; any valid SenderID works.
    public Optional<String> fetchAnySenderUid() {
        String senderUid = dsl.select(
                        field("({0}::jsonb->'HDR'->>'SenderID')", String.class, field(name("file_config"))))
                .from(table(name("product")))
                .where(field(name("file_config")).isNotNull())
                .limit(1)
                .fetchOne(0, String.class);
        return Optional.ofNullable(senderUid);
    }

    // HDC-175: Updates panel.status to 'SS-FHIR-Received' after successful FHIR download.
    public void updatePanelStatusFhirReceived(int panelId) {
        log.debug("HDC-175: Updating panel panelId={} status={}", panelId, STATUS_SS_FHIR_RECEIVED);
        dsl.update(table(name("panel")))
                .set(field(name("status")), STATUS_SS_FHIR_RECEIVED)
                .where(field(name("panel_id")).eq(panelId))
                .execute();
        log.debug("HDC-175: Updated panel panelId={} status={}", panelId, STATUS_SS_FHIR_RECEIVED);
    }

    // HDC-175: Updates panel.last_updated to now when no FHIR data is available for a panel.
    public void updatePanelLastUpdated(int panelId, OffsetDateTime now) {
        log.debug("HDC-175: Updating panel panelId={} last_updated={}", panelId, now);
        dsl.update(table(name("panel")))
                .set(field(name("last_updated")), now.toLocalDateTime())
                .where(field(name("panel_id")).eq(panelId))
                .execute();
        log.debug("HDC-175: Updated panel panelId={} last_updated={}", panelId, now);
    }

    // HDC-261: Updates panel.fhir_next_url after each FHIR page is written.
    // Pass null to clear the field when paging is complete.
    public void updatePanelFhirNextUrl(int panelId, String fhirNextUrl) {
        log.debug("HDC-261: Updating panel panelId={} fhir_next_url={}", panelId, fhirNextUrl);
        dsl.update(table(name("panel")))
                .set(field(name("fhir_next_url")), fhirNextUrl)
                .where(field(name("panel_id")).eq(panelId))
                .execute();
        log.debug("HDC-261: Updated panel panelId={} fhir_next_url={}", panelId, fhirNextUrl);
    }

    // HDC-261: Updates panel.status to ERROR-FHIR-PROCESSING when an exception occurs mid-paging.
    public void updatePanelStatusErrorFhirProcessing(int panelId) {
        log.debug("HDC-261: Updating panel panelId={} status={}", panelId, STATUS_ERROR_FHIR_PROCESSING);
        dsl.update(table(name("panel")))
                .set(field(name("status")), STATUS_ERROR_FHIR_PROCESSING)
                .where(field(name("panel_id")).eq(panelId))
                .execute();
        log.debug("HDC-261: Updated panel panelId={} status={}", panelId, STATUS_ERROR_FHIR_PROCESSING);
    }

    private PanelRecord toPanelRecord(Record r) {
        return new PanelRecord(
                r.get(field(name("panel_id")), Integer.class),
                r.get(field(name("reference_number")), String.class),
                r.get(field(name("status")), String.class),
                toOffsetDateTime(r.get(field(name("created_on")), LocalDateTime.class)),
                toOffsetDateTime(r.get(field(name("last_updated")), LocalDateTime.class)),
                r.get(field(name("data_source")), String.class),
                r.get(field(name("sent_request_filename")), String.class),
                // HDC-215: SenderID from product.file_config HDR used as X-SENDER-UID in FHIR requests.
                r.get(field(name("sender_uid")), String.class),
                // HDC-261: Resume URL for paging; null when no active page sequence.
                r.get(field(name("fhir_next_url")), String.class)
        );
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime ldt) {
        return ldt != null ? ldt.atOffset(ZoneOffset.UTC) : null;
    }
}
