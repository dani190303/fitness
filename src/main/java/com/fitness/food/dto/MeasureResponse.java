package com.fitness.food.dto;

import java.math.BigDecimal;

public record MeasureResponse(
        Long id,
        String name,
        BigDecimal baseAmount,
        boolean isDefault
) {
}
