package com.fitness.food.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UnitTypeConverter implements AttributeConverter<UnitType, String> {

    @Override
    public String convertToDatabaseColumn(UnitType attribute) {
        return attribute != null ? attribute.getValue() : null;
    }

    @Override
    public UnitType convertToEntityAttribute(String dbData) {
        return dbData != null ? UnitType.fromValue(dbData) : null;
    }
}
