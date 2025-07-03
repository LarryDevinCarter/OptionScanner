package com.larrydevincarter.optionscanner.services;

import java.util.List;

public interface AssetService {

    void fetchTradableAssets();

    List<String> fetchAndStoreIncomeStatements(List<String> errorLog, List<String> symbols);

    List<String> fetchAndStoreEarnings(List<String> errorLog, List<String> symbols);

    List<String> fetchAndStoreBalanceSheets(List<String> errorLog, List<String> symbols);

    List<String> fetchAndStoreCashFlows(List<String> errorLog, List<String> symbols);

    List<String> fetchAndStoreDividends(List<String> errorLog, List<String> symbols);

    void fetchAndStoreStockPrices(List<String> errorLog, List<String> symbols);
}
