package com.larrydevincarter.optionscanner.services;

import java.util.List;

public interface IncomeStatementService {

    void fetchAndStoreIncomeStatements();

    List<String> getSymbolsNeedingUpdate();

}
