package com.hcx.fhir.processor.model;

import java.time.OffsetDateTime;

// HDC-175: Immutable view of a panel row returned from the DB query.
// HDC-215: Added senderUid — extracted from product.file_config HDR.SenderID for X-SENDER-UID header.
public record PanelRecord(
        int panelId,
        String referenceNumber,
        String status,
        OffsetDateTime createdOn,
        OffsetDateTime lastUpdated,
        String dataSource,
        String sentRequestFilename,
        String senderUid
) {}
