package com.fitness.food.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateFoodRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 1, max = 120, message = "Name must be between 1 and 120 characters")
        String name,

        @NotNull(message = "Calories are required")
        @DecimalMin(value = "0.00", message = "Calories must be at least 0")
        @DecimalMax(value = "900.00", message = "Calories cannot exceed 900")
        @Digits(integer = 3, fraction = 2, message = "Calories can have at most 2 decimal places")
        BigDecimal caloriesKcal,

        @NotNull(message = "Protein is required")
        @DecimalMin(value = "0.00", message = "Protein must be at least 0")
        @DecimalMax(value = "100.00", message = "Protein cannot exceed 100")
        @Digits(integer = 3, fraction = 2, message = "Protein can have at most 2 decimal places")
        BigDecimal proteinG,

        @NotNull(message = "Carbohydrates are required")
        @DecimalMin(value = "0.00", message = "Carbohydrates must be at least 0")
        @DecimalMax(value = "100.00", message = "Carbohydrates cannot exceed 100")
        @Digits(integer = 3, fraction = 2, message = "Carbohydrates can have at most 2 decimal places")
        BigDecimal carbsG,

        @DecimalMin(value = "0.00", message = "Fat must be at least 0")
        @DecimalMax(value = "100.00", message = "Fat cannot exceed 100")
        @Digits(integer = 3, fraction = 2, message = "Fat can have at most 2 decimal places")
        BigDecimal fatG,

        @DecimalMin(value = "0.00", message = "Fiber must be at least 0")
        @DecimalMax(value = "100.00", message = "Fiber cannot exceed 100")
        @Digits(integer = 3, fraction = 2, message = "Fiber can have at most 2 decimal places")
        BigDecimal fiberG,

        @DecimalMin(value = "0.00", message = "Sugar must be at least 0")
        @DecimalMax(value = "100.00", message = "Sugar cannot exceed 100")
        @Digits(integer = 3, fraction = 2, message = "Sugar can have at most 2 decimal places")
        BigDecimal sugarG,

        @DecimalMin(value = "0.00", message = "Sodium must be at least 0")
        @DecimalMax(value = "40000.00", message = "Sodium cannot exceed 40000")
        @Digits(integer = 5, fraction = 2, message = "Sodium can have at most 2 decimal places")
        BigDecimal sodiumMg,

        @Size(max = 255, message = "Notes cannot exceed 255 characters")
        String notes
) {
    public UpdateFoodRequest {
        name = name != null ? name.trim() : null;
        notes = notes != null ? notes.trim() : null;
    }
}
