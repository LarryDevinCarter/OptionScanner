package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.models.entities.Dividend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DividendRepository extends JpaRepository<Dividend, Long> {

    @Modifying
    @Query("DELETE FROM Dividend d WHERE d.symbol = :symbol")
    void deleteBySymbol(String symbol);

    @Query("SELECT DISTINCT d.symbol FROM Dividend d WHERE d.lastUpdated >= :startOfDay")
    List<String> findSymbolsUpdatedToday(LocalDateTime startOfDay);

    @Query("SELECT DISTINCT a.symbol " +
            "FROM Asset a " +
            "LEFT JOIN Dividend d ON a.symbol = d.symbol " +
            "WHERE a.symbol IN :symbols " +
            "AND (d.symbol IS NULL OR " +
            "     d.exDividendDate < :date " +
            "     AND d.exDividendDate = (" +
            "         SELECT MAX(d2.exDividendDate) " +
            "         FROM Dividend d2 " +
            "         WHERE d2.symbol = d.symbol" +
            "     )" +
            ")")
    List<String> findSymbolsNeedingUpdate(@Param("date") LocalDate date, @Param("symbols") List<String> symbols);

    @Query("SELECT DISTINCT d.symbol " +
            "FROM Dividend d " +
            "WHERE d.symbol IN :symbols")
    List<String> findSymbolsThatHaveDividends(@Param("symbols") List<String> symbols);
}