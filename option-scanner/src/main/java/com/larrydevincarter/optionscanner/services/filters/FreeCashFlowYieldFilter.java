package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.models.FinancialReports;
import com.larrydevincarter.optionscanner.models.entities.Asset;
import com.larrydevincarter.optionscanner.models.entities.BalanceSheet;
import com.larrydevincarter.optionscanner.models.entities.CashFlow;
import com.larrydevincarter.optionscanner.utils.FinancialFilterUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Data
@AllArgsConstructor
public class FreeCashFlowYieldFilter implements FinancialFilter {

    private double fcfYieldThreshold;

    @Override
    public boolean appliesTo(String symbol, FinancialReports reports) {
        double fcfYield = calculateFcfYield(symbol, reports);
        if (fcfYield < 0) {
            return false;
        }
        log.debug("FCF Yield for symbol {}: {}% (threshold: {}%)", symbol, fcfYield, fcfYieldThreshold);
        return fcfYield > fcfYieldThreshold;
    }

    public double calculateFcfYield(String symbol, FinancialReports reports) {
        List<CashFlow> cashFlows = FinancialFilterUtils.getAnnualReports(
                reports.getCashFlows(), 1,
                c -> c.getOperatingCashflow() != null && c.getCapitalExpenditures() != null,
                FinancialFilterUtils.CASHFLOW_DATE_EXTRACTOR
        );

        List<BalanceSheet> balanceSheets = FinancialFilterUtils.getAnnualReports(
                reports.getBalanceSheets(), 1,
                b -> b.getCommonStockSharesOutstanding() != null,
                FinancialFilterUtils.BALANCE_DATE_EXTRACTOR
        );

        List<Asset> assets = FinancialFilterUtils.getValidAssets(reports.getAssets());

        if (cashFlows.isEmpty() || balanceSheets.isEmpty() || assets.isEmpty()) {
            return INVALID_RESULT;
        }

        CashFlow latestCashFlow = cashFlows.getFirst();
        BalanceSheet latestBalanceSheet = balanceSheets.getFirst();
        Asset asset = assets.getFirst();

        if (latestCashFlow.getFiscalDateEnding().getYear() != latestBalanceSheet.getFiscalDateEnding().getYear()) {
            log.warn("Year mismatch for {}: CashFlow={}, BalanceSheet={}",
                    symbol, latestCashFlow.getFiscalDateEnding().getYear(), latestBalanceSheet.getFiscalDateEnding().getYear());
            return INVALID_RESULT;
        }

        double freeCashFlow = latestCashFlow.getOperatingCashflow() - latestCashFlow.getCapitalExpenditures();
        if (freeCashFlow <= 0) {
            return INVALID_RESULT;
        }

        double sharesOutstanding = latestBalanceSheet.getCommonStockSharesOutstanding();
        double currentPrice = asset.getCurrentPrice();
        double marketCap = currentPrice * sharesOutstanding;
        if (marketCap <= 0) {
            return INVALID_RESULT;
        }

        return (freeCashFlow / marketCap) * 100;
    }

    @Override
    public String getName() {
        return "Free Cash Flow Yield";
    }
}