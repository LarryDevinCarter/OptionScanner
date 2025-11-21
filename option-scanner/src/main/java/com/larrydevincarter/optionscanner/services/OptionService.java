package com.larrydevincarter.optionscanner.services;

import com.larrydevincarter.optionscanner.models.dtos.OwnedAssetDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface OptionService {
    void processOptionsForSymbol(String symbol, List<String> errorLog, Set<LocalDate> tradingDays, LocalDate previousTradingDay);

    void fetchCoveredCallOptions(OwnedAssetDTO ownedAsset);
}