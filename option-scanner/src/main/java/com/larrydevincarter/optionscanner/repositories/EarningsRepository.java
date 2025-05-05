package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.entities.Earnings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EarningsRepository extends JpaRepository<Earnings, Long> {

    void deleteBySymbol(String symbol);

    @Query("SELECT DISTINCT e.symbol FROM Earnings e WHERE e.lastUpdated >= :startOfDay")
    List<String> findSymbolsUpdatedToday(LocalDateTime startOfDay);

    @Query("SELECT DISTINCT a.symbol " +
            "FROM Asset a " +
            "INNER JOIN IncomeStatement i ON a.symbol = i.symbol " +
            "LEFT JOIN Earnings e ON a.symbol = e.symbol " +
            "WHERE a.status = 'active' AND a.tradable = true " +
            "AND i.fiscalDateEnding >= :date " +
            "AND (e.symbol IS NULL OR " +
            "     a.symbol NOT IN (" +
            "         SELECT e2.symbol " +
            "         FROM Earnings e2 " +
            "         WHERE e2.fiscalDateEnding >= :date " +
            "         AND e2.symbol = a.symbol " +
            "     )" +
            ")")
    List<String> findActiveTradableSymbolsNeedingUpdate(LocalDate date);
}