package com.fitness.food.service;

import com.fitness.common.exception.ConflictException;
import com.fitness.common.exception.ResourceNotFoundException;
import com.fitness.common.util.SecurityUtils;
import com.fitness.food.dto.CreateFoodRequest;
import com.fitness.food.dto.FoodResponse;
import com.fitness.food.dto.MeasureRequest;
import com.fitness.food.dto.MeasureResponse;
import com.fitness.food.dto.UpdateFoodRequest;
import com.fitness.food.entity.Food;
import com.fitness.food.entity.FoodMeasure;
import com.fitness.food.repository.FoodRepository;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FoodService {

    private final FoodRepository foodRepository;

    public FoodService(FoodRepository foodRepository) {
        this.foodRepository = foodRepository;
    }

    @Transactional(readOnly = true)
    public List<FoodResponse> getFoods(String q, boolean includeArchived) {
        Long userId = SecurityUtils.getCurrentUserId();
        String trimmedQ = (q != null && !q.isBlank()) ? q.trim() : null;
        List<Food> foods = foodRepository.findAllByUserIdWithFilters(userId, includeArchived, trimmedQ);
        return foods.stream().map(FoodService::toFoodResponse).toList();
    }

    @Transactional(readOnly = true)
    public FoodResponse getFood(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        Food food = foodRepository.findByIdAndUserIdWithMeasures(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Food not found with id: " + id));
        return toFoodResponse(food);
    }

    @Transactional
    public FoodResponse createFood(CreateFoodRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();

        Optional<Food> existing = foodRepository.findByUserIdAndName(userId, request.name());
        if (existing.isPresent()) {
            if (existing.get().isArchived()) {
                throw new ConflictException("A food with name '" + request.name() + "' already exists in archived foods. Consider unarchiving it instead.");
            }
            throw new ConflictException("A food with name '" + request.name() + "' already exists.");
        }

        Food food = new Food(
                userId,
                request.name(),
                request.unitType(),
                request.caloriesKcal(),
                request.proteinG(),
                request.carbsG(),
                request.fatG(),
                request.fiberG(),
                request.sugarG(),
                request.sodiumMg(),
                request.notes()
        );

        if (request.measures() != null && !request.measures().isEmpty()) {
            Set<String> seenNames = new HashSet<>();
            for (MeasureRequest mr : request.measures()) {
                if (!seenNames.add(mr.name().toLowerCase())) {
                    throw new ConflictException("Duplicate measure name in request: " + mr.name());
                }
            }

            long defaultCount = request.measures().stream()
                    .filter(m -> Boolean.TRUE.equals(m.isDefault()))
                    .count();

            if (defaultCount > 1) {
                throw new IllegalArgumentException("Only one measure can be marked as default");
            }

            boolean hasExplicitDefault = (defaultCount == 1);

            for (int i = 0; i < request.measures().size(); i++) {
                MeasureRequest mr = request.measures().get(i);
                boolean isDefault = hasExplicitDefault ? Boolean.TRUE.equals(mr.isDefault()) : (i == 0);
                FoodMeasure measure = new FoodMeasure(mr.name(), mr.baseAmount(), isDefault);
                food.addMeasure(measure);
            }
        }

        Food savedFood = foodRepository.save(food);
        return toFoodResponse(savedFood);
    }

    @Transactional
    public FoodResponse updateFood(Long id, UpdateFoodRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();

        Food food = foodRepository.findByIdAndUserIdWithMeasures(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Food not found with id: " + id));

        Optional<Food> existing = foodRepository.findByUserIdAndName(userId, request.name());
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            if (existing.get().isArchived()) {
                throw new ConflictException("A food with name '" + request.name() + "' already exists in archived foods. Consider unarchiving it instead.");
            }
            throw new ConflictException("A food with name '" + request.name() + "' already exists.");
        }

        food.setName(request.name());
        food.setCaloriesKcal(request.caloriesKcal());
        food.setProteinG(request.proteinG());
        food.setCarbsG(request.carbsG());
        food.setFatG(request.fatG());
        food.setFiberG(request.fiberG());
        food.setSugarG(request.sugarG());
        food.setSodiumMg(request.sodiumMg());
        food.setNotes(request.notes());

        Food updatedFood = foodRepository.save(food);
        return toFoodResponse(updatedFood);
    }

    @Transactional
    public FoodResponse archiveFood(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();

        Food food = foodRepository.findByIdAndUserIdWithMeasures(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Food not found with id: " + id));

        food.setArchived(true);
        Food updated = foodRepository.save(food);
        return toFoodResponse(updated);
    }

    @Transactional
    public FoodResponse unarchiveFood(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();

        Food food = foodRepository.findByIdAndUserIdWithMeasures(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Food not found with id: " + id));

        food.setArchived(false);
        Food updated = foodRepository.save(food);
        return toFoodResponse(updated);
    }

    public static FoodResponse toFoodResponse(Food food) {
        return new FoodResponse(
                food.getId(),
                food.getName(),
                food.getUnitType(),
                food.getCaloriesKcal(),
                food.getProteinG(),
                food.getCarbsG(),
                food.getFatG(),
                food.getFiberG(),
                food.getSugarG(),
                food.getSodiumMg(),
                food.getNotes(),
                food.isArchived(),
                toMeasureResponseList(food.getMeasures())
        );
    }

    public static List<MeasureResponse> toMeasureResponseList(List<FoodMeasure> measures) {
        if (measures == null) {
            return List.of();
        }
        return measures.stream()
                .sorted(Comparator.comparing(FoodMeasure::isDefault, Comparator.reverseOrder())
                        .thenComparing(FoodMeasure::getName, String.CASE_INSENSITIVE_ORDER))
                .map(m -> new MeasureResponse(m.getId(), m.getName(), m.getBaseAmount(), m.isDefault()))
                .toList();
    }
}
