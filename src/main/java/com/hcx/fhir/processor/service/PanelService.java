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
                        field("({0}::jsonb->'HDR'->>'SenderID')", String.class, field(name("pr", "file_config"))).as("sender_uid"))
                .from(table(name("panel")).as("p"))
                .join(table(name("product")).as("pr"))
                .on(field(name("p", "product_id")).eq(field(name("pr", "product_id"))))
                .where(field(name("p", "status")).eq(STATUS_SS_LOADED))
                .fetch(this::toPanelRecord);
        log.debug("HDC-175: Found {} SS-Loaded panel(s)", panels.size());
        return panels;
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
                r.get(field(name("sender_uid")), String.class)
        );
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime ldt) {
        return ldt != null ? ldt.atOffset(ZoneOffset.UTC) : null;
    }
}
