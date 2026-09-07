package com.larrydevincarter.optionscanner.services;

import java.time.LocalDateTime;

/**
 * Informational refresh progress only.
 * Consumers (including trading-system) MUST NOT block reads on this status.
 * Reads remain available while a refresh is in progress; the DB keeps last-good rows.
 */
public interface UpdateStatusService {

    /** @deprecated Prefer {@link #isRefreshInProgress()}; kept for API compatibility. */
    boolean isUpdating();

    boolean isRefreshInProgress();

    LocalDateTime getLastRefreshStarted();

    LocalDateTime getLastRefreshFinished();

    /**
     * Marks refresh as started if none is running.
     * @return true if this caller acquired the in-process refresh marker; false if already running
     */
    boolean tryBeginRefresh();

    void markRefreshFinished();
}
