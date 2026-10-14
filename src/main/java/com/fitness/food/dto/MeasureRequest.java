package com.fitness.food.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record MeasureRequest(
        @NotBlank(message = "Measure name is required")
        @Size(min = 1, max = 60, message = "Measure name must be between 1 and 60 characters")
        String name,

        @NotNull(message = "Base amount is required")
        @DecimalMin(value = "0.01", message = "Base amount must be at least 0.01")
        @DecimalMax(value = "10000.00", message = "Base amount cannot exceed 10000")
        @Digits(integer = 5, fraction = 2, message = "Base amount can have at most 2 decimal places")
        BigDecimal baseAmount,

        Boolean isDefault
) {
    public MeasureRequest {
        name = name != null ? name.trim() : null;
        if (isDefault == null) {
            isDefault = false;
        }
    }
}
