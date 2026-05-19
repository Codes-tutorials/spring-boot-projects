package org.codeart.saga.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * Embeddable SAGA step for tracking execution history.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SagaStep {
    private String stepName;
    private String status; // SUCCESS, FAILED, COMPENSATED
    private String details;
    private Instant timestamp;
}
