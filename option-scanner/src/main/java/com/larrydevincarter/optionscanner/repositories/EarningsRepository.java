package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.entities.Earnings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface EarningsRepository extends JpaRepository<Earnings, Long> {

    void deleteBySymbol(String symbol);

    @Query("SELECT DISTINCT e.symbol FROM Earnings e WHERE e.lastUpdated >= :startOfDay")
    List<String> findSymbolsUpdatedToday(LocalDateTime startOfDay);
}