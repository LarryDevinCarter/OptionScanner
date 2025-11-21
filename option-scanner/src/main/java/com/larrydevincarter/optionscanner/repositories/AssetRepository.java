package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.models.entities.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing Asset entities.
 */
@Repository
public interface AssetRepository extends JpaRepository<Asset, String> {

    /**
     * Finds all active assets with data older than the cutoff time.
     *
     * @param cutoff the cutoff time for staleness
     * @return list of stale active assets
     */
    @Query("SELECT a FROM Asset a WHERE a.status = 'active' AND a.lastUpdated < :cutoff")
    List<Asset> findActiveStaleAssets(LocalDateTime cutoff);

    /**
     * Finds symbols of all active and tradable assets.
     *
     * @return list of active and tradable asset symbols
     */
    @Query("SELECT a.symbol FROM Asset a WHERE a.status = 'active' AND a.tradable = true")
    List<String> findActiveTradableSymbols();

    /**
     * Finds an asset by its symbol.
     *
     * @param symbol the symbol to query
     * @return an Optional containing the asset, or empty if not found
     */
    Optional<Asset> findBySymbol(String symbol);

    /**
     * Finds assets by a list of symbols.
     *
     * @param symbols the list of symbols to query
     * @return list of assets matching the provided symbols
     */
    @Query("SELECT a FROM Asset a WHERE a.symbol IN :symbols")
    List<Asset> findBySymbols(List<String> symbols);

    /**
     * Deletes an asset by its symbol.
     *
     * @param symbol the symbol of the asset to delete
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Asset a WHERE a.symbol = :symbol")
    void deleteBySymbol(@Param("symbol") String symbol);
}
