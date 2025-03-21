package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.entities.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AssetRepository extends JpaRepository<Asset, String> {

    @Query("SELECT a FROM Asset a WHERE a.status = 'active' AND a.lastUpdated < :cutoff")
    List<Asset> findActiveStaleAssets(LocalDateTime cutoff);

}
