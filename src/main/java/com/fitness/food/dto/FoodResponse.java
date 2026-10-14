package com.fitness.food.dto;

import com.fitness.food.entity.UnitType;
import java.math.BigDecimal;
import java.util.List;

public record FoodResponse(
        Long id,
        String name,
        UnitType unitType,
        BigDecimal caloriesKcal,
        BigDecimal proteinG,
        BigDecimal carbsG,
        BigDecimal fatG,
        BigDecimal fiberG,
        BigDecimal sugarG,
        BigDecimal sodiumMg,
        String notes,
        boolean archived,
        List<MeasureResponse> measures
) {
}
