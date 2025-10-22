package com.larrydevincarter.optionscanner.models.entities;

import java.time.LocalDate;

/**
 * Marker interface for financial entities with fiscalDateEnding.
 */
public interface HasFiscalDate {
    LocalDate getFiscalDateEnding();
}