package com.larrydevincarter.optionscanner.services;

import java.util.List;

public interface AssetService {

    void fetchTradableAssets();

    void fetchAndStoreStockPrices(List<String> errorLog, List<String> symbols);

    void fetchAndStoreOptions(List<String> errorLog, List<String> symbols);

    void writeErrorReport();
}
