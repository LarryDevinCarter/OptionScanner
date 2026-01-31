package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.services.UpdateStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class UpdateStatusServiceImpl implements UpdateStatusService {

    private final AtomicBoolean updating = new AtomicBoolean(false);

    @Override
    public boolean isUpdating() {
        return updating.get();
    }

    @Override
    public void setUpdating(boolean value) {
        updating.set(value);
        log.info("Database update status changed to: {}", value);
    }
}