package com.larrydevincarter.optionscanner.mappers;

import com.larrydevincarter.optionscanner.entities.StockOverview;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Map;

@Mapper(componentModel = "spring")
public interface StockOverviewMapper {

    @Mapping(target = "ticker", source = "Symbol", qualifiedByName = "parseStringOrDefault")
    @Mapping(target = "peRatio", source = "PERatio", qualifiedByName = "parseDoubleOrDefault")
    @Mapping(target = "eps", source = "EPS", qualifiedByName = "parseDoubleOrDefault")
    @Mapping(target = "dividendYield", source = "DividendYield", qualifiedByName = "parseDoubleOrDefault")
    @Mapping(target = "marketCap", source = "MarketCapitalization", qualifiedByName = "parseLongOrDefault")
    @Mapping(target = "bookValue", source = "BookValue", qualifiedByName = "parseDoubleOrDefault")
    @Mapping(target = "lastUpdated", expression = "java(java.time.LocalDateTime.now())")
    StockOverview toEntity(Map<String, Object> overviewData);

    @Named("parseDoubleOrDefault")
    default double parseDoubleOrDefault(Object value) {

        if (value == null || "N/A".equals(value)) return 0.0;

        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    @Named("parseLongOrDefault")
    default long parseLongOrDefault(Object value) {

        if (value == null || "N/A".equals(value)) return 0L;

        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Named("parseStringOrDefault")
    default String parseStringOrDefault(Object value) {
        if (value == null || "N/A".equals(value.toString())) {
            return "";
        }
        return value.toString();
    }
}
