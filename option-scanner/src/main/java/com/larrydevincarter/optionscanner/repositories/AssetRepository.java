package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.entities.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, String> {

}
