package com.larrydevincarter.optionscanner.services;

import com.larrydevincarter.optionscanner.models.dtos.OwnedAssetDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface OptionService {

    void fetchAndStoreOptionsForSymbol(String symbol, Set<LocalDate> tradingDays);
    void processOptionsForSymbol(String symbol, List<String> errorLog, Set<LocalDate> tradingDays, LocalDate previousTradingDay);

    void fetchCoveredCallOptions(OwnedAssetDto ownedAsset);
}