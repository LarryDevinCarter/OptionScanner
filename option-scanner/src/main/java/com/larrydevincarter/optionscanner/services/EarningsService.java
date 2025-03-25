package com.larrydevincarter.optionscanner.services;

import java.util.List;

public interface EarningsService {

    void fetchAndStoreEarnings(List<String> errorLog);

    List<String> getSymbolsWithUpdatedIncomeStatements();
}
