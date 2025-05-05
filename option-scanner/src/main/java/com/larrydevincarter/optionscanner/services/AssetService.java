package com.larrydevincarter.optionscanner.services;

import java.util.List;

public interface AssetService {

    void fetchTradableAssets();

    void fetchAndStoreIncomeStatements(List<String> errorLog);

    void fetchAndStoreEarnings(List<String> errorLog);
}
