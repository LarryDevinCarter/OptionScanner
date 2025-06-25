package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.entities.CashFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CashFlowRepository extends JpaRepository<CashFlow, Long> {

    void deleteBySymbol(String symbol);

    @Query("SELECT DISTINCT a.symbol " +
            "FROM Asset a " +
            "INNER JOIN IncomeStatement i ON a.symbol = i.symbol " +
            "INNER JOIN Earnings e ON a.symbol = e.symbol " +
            "INNER JOIN BalanceSheet b ON a.symbol = b.symbol " +
            "LEFT JOIN CashFlow c ON a.symbol = c.symbol " +
            "WHERE a.status = 'active' AND a.tradable = true " +
            "AND (c.symbol IS NULL OR " +
            "     a.symbol NOT IN (" +
            "         SELECT c2.symbol " +
            "         FROM CashFlow c2 " +
            "         WHERE c2.fiscalDateEnding >= :date " +
            "         AND c2.symbol = a.symbol " +
            "     ))")
    List<String> findActiveTradableSymbolsNeedingUpdate(@Param("date") LocalDate date);

    @Query("SELECT DISTINCT a.symbol " +
            "FROM Asset a " +
            "LEFT JOIN CashFlow c ON a.symbol = c.symbol " +
            "WHERE a.symbol IN :symbols " +
            "AND (c.symbol IS NULL OR " +
            "     c.fiscalDateEnding < :date " +
            "     AND c.fiscalDateEnding = (" +
            "         SELECT MAX(c2.fiscalDateEnding) " +
            "         FROM CashFlow c2 " +
            "         WHERE c2.symbol = c.symbol" +
            "     )" +
            ")")
    List<String> findSymbolsNeedingUpdate(@Param("date") LocalDate date, @Param("symbols") List<String> symbols);

    @Query("SELECT DISTINCT c.symbol " +
            "FROM CashFlow c " +
            "WHERE c.symbol IN :symbols")
    List<String> findSymbolsThatHaveStatements(@Param("symbols") List<String> symbols);
}