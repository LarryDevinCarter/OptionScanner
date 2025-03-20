package com.larrydevincarter.optionscanner.mappers;

import com.larrydevincarter.optionscanner.dtos.PortfolioInputDTO;
import com.larrydevincarter.optionscanner.entities.Portfolio;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PortfolioMapper {

    @Mapping(target = "acquisitionDate", expression = "java(java.time.LocalDateTime.now())")
    Portfolio toEntity(PortfolioInputDTO dto);
}
