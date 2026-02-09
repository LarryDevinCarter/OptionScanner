package com.larrydevincarter.optionscanner.models.dtos;

import com.larrydevincarter.optionscanner.models.entities.Asset;
import com.larrydevincarter.optionscanner.models.entities.Option;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class OptionBatchResponseDto {
    private List<Asset> assets;
    private Map<String, List<Option>> optionChains;
}