package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.models.entities.BalanceSheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BalanceSheetRepository extends JpaRepository<BalanceSheet, Long> {

    void deleteBySymbol(String symbol);

    @Query("SELECT DISTINCT a.symbol " +
            "FROM Asset a " +
            "INNER JOIN IncomeStatement i ON a.symbol = i.symbol " +
            "INNER JOIN Earnings e ON a.symbol = e.symbol " +
            "LEFT JOIN BalanceSheet b ON a.symbol = b.symbol " +
            "WHERE a.status = 'active' AND a.tradable = true " +
            "AND (b.symbol IS NULL OR " +
            "     a.symbol NOT IN (" +
            "         SELECT b2.symbol " +
            "         FROM BalanceSheet b2 " +
            "         WHERE b2.fiscalDateEnding >= :date " +
            "         AND b2.symbol = a.symbol " +
            "     ))")
    List<String> findActiveTradableSymbolsNeedingUpdate(LocalDate date);

    @Query("SELECT DISTINCT a.symbol " +
            "FROM Asset a " +
            "LEFT JOIN BalanceSheet b ON a.symbol = b.symbol " +
            "WHERE a.symbol IN :symbols " +
            "AND (b.symbol IS NULL OR " +
            "     b.fiscalDateEnding < :date " +
            "     AND b.fiscalDateEnding = (" +
            "         SELECT MAX(b2.fiscalDateEnding) " +
            "         FROM BalanceSheet b2 " +
            "         WHERE b2.symbol = b.symbol" +
            "     )" +
            ")")
    List<String> findSymbolsNeedingUpdate(@Param("date") LocalDate date, @Param("symbols") List<String> symbols);

    @Query("SELECT DISTINCT b.symbol " +
            "FROM BalanceSheet b " +
            "WHERE b.symbol IN :symbols")
    List<String> findSymbolsThatHaveStatements(@Param("symbols") List<String> symbols);

    Optional<BalanceSheet> findTopBySymbolAndReportTypeOrderByFiscalDateEndingDesc(String symbol, String reportType);

    List<BalanceSheet> findBySymbol(String symbol);
}