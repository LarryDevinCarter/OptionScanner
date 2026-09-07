package com.larrydevincarter.optionscanner.models.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Informational refresh progress. Consumers MUST NOT block data reads on these fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStatusDto {

    /** Legacy alias for {@link #refreshInProgress}. */
    @JsonProperty("isUpdating")
    private boolean isUpdating;

    @JsonProperty("refreshInProgress")
    private boolean refreshInProgress;

    private LocalDateTime lastRefreshStarted;

    private LocalDateTime lastRefreshFinished;
}
