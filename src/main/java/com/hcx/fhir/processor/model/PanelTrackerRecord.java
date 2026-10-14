package com.hcx.fhir.processor.model;

import java.time.OffsetDateTime;

// HDC-308: Immutable view of a row written to healthdata.panel_tracker — one row per
// panel status mark (success, retry, or terminal error) for external progress monitoring.
public record PanelTrackerRecord(
        int panelId,
        String status,
        String app,
        int retryCount,
        OffsetDateTime createdOn,
        String logStream
) {}
