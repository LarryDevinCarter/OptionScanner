package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.entities.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, String> {

    @Query("SELECT a FROM Asset a WHERE a.status = 'active' AND a.lastUpdated < :cutoff")
    List<Asset> findActiveStaleAssets(LocalDateTime cutoff);

    @Query("SELECT a.symbol FROM Asset a WHERE a.status = 'active' AND a.tradable = true")
    List<String> findActiveTradableSymbols();

    Optional<Asset> findBySymbol(String symbol);

    @Query("SELECT a FROM Asset a WHERE a.symbol IN :symbols")
    List<Asset> findBySymbols(List<String> symbols);


}
