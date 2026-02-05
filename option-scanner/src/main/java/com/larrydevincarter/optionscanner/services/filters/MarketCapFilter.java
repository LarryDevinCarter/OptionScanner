package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.models.FinancialReports;
import com.larrydevincarter.optionscanner.models.entities.Asset;
import com.larrydevincarter.optionscanner.models.entities.BalanceSheet;
import com.larrydevincarter.optionscanner.utils.FinancialFilterUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Data
@AllArgsConstructor
public class MarketCapFilter implements FinancialFilter {

    private static final double INVALID_RESULT = -99999.0;

    private long minMarketCap;

    @Override
    public boolean appliesTo(String symbol, FinancialReports reports) {
        if ("TSLA".equals(symbol)) {
            return true;
        }
        double marketCap = calculateMarketCap(reports);
        if (marketCap == INVALID_RESULT) {
            return false;
        }
        return marketCap >= minMarketCap;
    }

    private double calculateMarketCap(FinancialReports reports) {
        List<BalanceSheet> balanceSheets = FinancialFilterUtils.getAnnualReports(
                reports.getBalanceSheets(),
                1,
                s -> s.getCommonStockSharesOutstanding() != null,
                FinancialFilterUtils.BALANCE_DATE_EXTRACTOR
        );
        if (balanceSheets.isEmpty()) {
            return INVALID_RESULT;
        }
        BalanceSheet latest = balanceSheets.get(0);
        double shares = latest.getCommonStockSharesOutstanding();
        List<Asset> assets = FinancialFilterUtils.getValidAssets(reports.getAssets());
        if (assets.isEmpty()) {
            return INVALID_RESULT;
        }
        double price = assets.get(0).getCurrentPrice();
        return price * shares;
    }

    @Override
    public String getName() {
        return "Market Cap";
    }
}