package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.services.UpdateStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks refresh progress for observability only.
 * Does NOT lock out API reads — last-good DB rows stay readable during refresh.
 */
@Service
@Slf4j
public class UpdateStatusServiceImpl implements UpdateStatusService {

    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    private final AtomicReference<LocalDateTime> lastRefreshStarted = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> lastRefreshFinished = new AtomicReference<>();

    @Override
    public boolean isUpdating() {
        return isRefreshInProgress();
    }

    @Override
    public boolean isRefreshInProgress() {
        return refreshInProgress.get();
    }

    @Override
    public LocalDateTime getLastRefreshStarted() {
        return lastRefreshStarted.get();
    }

    @Override
    public LocalDateTime getLastRefreshFinished() {
        return lastRefreshFinished.get();
    }

    @Override
    public boolean tryBeginRefresh() {
        if (!refreshInProgress.compareAndSet(false, true)) {
            return false;
        }
        lastRefreshStarted.set(LocalDateTime.now());
        log.info("Refresh progress: started (informational only — reads remain available)");
        return true;
    }

    @Override
    public void markRefreshFinished() {
        refreshInProgress.set(false);
        lastRefreshFinished.set(LocalDateTime.now());
        log.info("Refresh progress: finished (informational only)");
    }
}
