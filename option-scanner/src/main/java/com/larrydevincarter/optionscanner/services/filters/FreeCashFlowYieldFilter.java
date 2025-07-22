package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.entities.Asset;
import com.larrydevincarter.optionscanner.entities.BalanceSheet;
import com.larrydevincarter.optionscanner.entities.CashFlow;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Data
@AllArgsConstructor
public class FreeCashFlowYieldFilter implements FinancialFilter<Object> {

    private double fcfYieldThreshold;

    @Override
    public boolean appliesTo(String symbol, List<Object> reports) {
        double fcfYield = calculateFcfYield(symbol, reports);
        if (fcfYield < 0) {
            return false;
        }
        log.debug("FCF Yield for symbol {}: {}% (threshold: {}%)", symbol, fcfYield, fcfYieldThreshold);
        return fcfYield > fcfYieldThreshold;
    }

    public double calculateFcfYield(String symbol, List<Object> reports) {
        List<CashFlow> cashFlows = reports.stream()
                .filter(r -> r instanceof CashFlow)
                .map(r -> (CashFlow) r)
                .filter(c -> "annual".equals(c.getReportType()))
                .filter(c -> c.getOperatingCashflow() != null && c.getCapitalExpenditures() != null)
                .sorted((c1, c2) -> c2.getFiscalDateEnding().compareTo(c1.getFiscalDateEnding()))
                .toList();

        List<BalanceSheet> balanceSheets = reports.stream()
                .filter(r -> r instanceof BalanceSheet)
                .map(r -> (BalanceSheet) r)
                .filter(b -> "annual".equals(b.getReportType()))
                .filter(b -> b.getCommonStockSharesOutstanding() != null)
                .sorted((b1, b2) -> b2.getFiscalDateEnding().compareTo(b1.getFiscalDateEnding()))
                .toList();

        List<Asset> assets = reports.stream()
                .filter(r -> r instanceof Asset)
                .map(r -> (Asset) r)
                .filter(a -> a.getCurrentPrice() != null)
                .toList();

        if (cashFlows.isEmpty() || balanceSheets.isEmpty() || assets.isEmpty()) {
            return -1.0;
        }

        CashFlow latestCashFlow = cashFlows.getFirst();
        BalanceSheet latestBalanceSheet = balanceSheets.getFirst();
        Asset asset = assets.getFirst();

        if (latestCashFlow.getFiscalDateEnding().getYear() != latestBalanceSheet.getFiscalDateEnding().getYear()) {
            return -1.0;
        }

        double freeCashFlow = latestCashFlow.getOperatingCashflow() - latestCashFlow.getCapitalExpenditures();
        if (freeCashFlow <= 0) {
            return -1.0;
        }

        double sharesOutstanding = latestBalanceSheet.getCommonStockSharesOutstanding();
        double currentPrice = asset.getCurrentPrice();
        double marketCap = currentPrice * sharesOutstanding;
        if (marketCap <= 0) {
            return -1.0;
        }

        return (freeCashFlow / marketCap) * 100;
    }

    @Override
    public String getName() {
        return "Free Cash Flow Yield";
    }
}