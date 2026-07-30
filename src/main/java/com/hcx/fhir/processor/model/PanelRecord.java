package com.hcx.fhir.processor.model;

import java.time.OffsetDateTime;

// HDC-175: Immutable view of a panel row returned from the DB query.
public record PanelRecord(
        int panelId,
        String referenceNumber,
        String status,
        OffsetDateTime createdOn,
        OffsetDateTime lastUpdated,
        String dataSource,
        String sentRequestFilename
) {}
