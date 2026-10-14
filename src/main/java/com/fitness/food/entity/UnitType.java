package com.fitness.food.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum UnitType {
    G("g"),
    ML("ml");

    private final String value;

    UnitType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static UnitType fromValue(String value) {
        if (value != null) {
            String trimmed = value.trim();
            for (UnitType ut : values()) {
                if (ut.value.equalsIgnoreCase(trimmed) || ut.name().equalsIgnoreCase(trimmed)) {
                    return ut;
                }
            }
        }
        throw new IllegalArgumentException("Invalid unit_type: '" + value + "'. Allowed values are: 'g', 'ml'");
    }
}
