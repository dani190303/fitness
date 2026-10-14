package com.fitness.food.service;

import com.fitness.common.exception.ConflictException;
import com.fitness.common.exception.ResourceNotFoundException;
import com.fitness.common.util.SecurityUtils;
import com.fitness.food.dto.MeasureRequest;
import com.fitness.food.dto.MeasureResponse;
import com.fitness.food.entity.Food;
import com.fitness.food.entity.FoodMeasure;
import com.fitness.food.repository.FoodMeasureRepository;
import com.fitness.food.repository.FoodRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FoodMeasureService {

    private final FoodRepository foodRepository;
    private final FoodMeasureRepository foodMeasureRepository;

    public FoodMeasureService(FoodRepository foodRepository, FoodMeasureRepository foodMeasureRepository) {
        this.foodRepository = foodRepository;
        this.foodMeasureRepository = foodMeasureRepository;
    }

    @Transactional(readOnly = true)
    public List<MeasureResponse> getMeasures(Long foodId) {
        Long userId = SecurityUtils.getCurrentUserId();
        foodRepository.findByIdAndUserId(foodId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Food not found with id: " + foodId));

        List<FoodMeasure> measures = foodMeasureRepository.findAllByFoodId(foodId);
        return measures.stream()
                .sorted(Comparator.comparing(FoodMeasure::isDefault, Comparator.reverseOrder())
                        .thenComparing(FoodMeasure::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MeasureResponse createMeasure(Long foodId, MeasureRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Food food = foodRepository.findByIdAndUserId(foodId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Food not found with id: " + foodId));

        if (foodMeasureRepository.findByFoodIdAndName(foodId, request.name()).isPresent()) {
            throw new ConflictException("A measure with name '" + request.name() + "' already exists for this food.");
        }

        List<FoodMeasure> existingMeasures = foodMeasureRepository.findAllByFoodId(foodId);
        boolean shouldBeDefault = false;

        if (existingMeasures.isEmpty()) {
            shouldBeDefault = true;
        } else if (Boolean.TRUE.equals(request.isDefault())) {
            shouldBeDefault = true;
            for (FoodMeasure m : existingMeasures) {
                if (m.isDefault()) {
                    m.setDefault(false);
                    foodMeasureRepository.save(m);
                }
            }
        }

        FoodMeasure measure = new FoodMeasure(request.name(), request.baseAmount(), shouldBeDefault);
        measure.setFood(food);
        FoodMeasure saved = foodMeasureRepository.save(measure);

        return toResponse(saved);
    }

    @Transactional
    public MeasureResponse updateMeasure(Long foodId, Long measureId, MeasureRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        foodRepository.findByIdAndUserId(foodId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Food not found with id: " + foodId));

        FoodMeasure measure = foodMeasureRepository.findByIdAndFoodId(measureId, foodId)
                .orElseThrow(() -> new ResourceNotFoundException("Measure not found with id: " + measureId));

        Optional<FoodMeasure> existingSameName = foodMeasureRepository.findByFoodIdAndName(foodId, request.name());
        if (existingSameName.isPresent() && !existingSameName.get().getId().equals(measureId)) {
            throw new ConflictException("A measure with name '" + request.name() + "' already exists for this food.");
        }

        measure.setName(request.name());
        measure.setBaseAmount(request.baseAmount());

        if (Boolean.TRUE.equals(request.isDefault())) {
            if (!measure.isDefault()) {
                List<FoodMeasure> allMeasures = foodMeasureRepository.findAllByFoodId(foodId);
                for (FoodMeasure m : allMeasures) {
                    if (m.isDefault() && !m.getId().equals(measureId)) {
                        m.setDefault(false);
                        foodMeasureRepository.save(m);
                    }
                }
                measure.setDefault(true);
            }
        } else {
            measure.setDefault(false);
        }

        FoodMeasure saved = foodMeasureRepository.save(measure);
        return toResponse(saved);
    }

    @Transactional
    public void deleteMeasure(Long foodId, Long measureId) {
        Long userId = SecurityUtils.getCurrentUserId();
        foodRepository.findByIdAndUserId(foodId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Food not found with id: " + foodId));

        FoodMeasure measure = foodMeasureRepository.findByIdAndFoodId(measureId, foodId)
                .orElseThrow(() -> new ResourceNotFoundException("Measure not found with id: " + measureId));

        foodMeasureRepository.delete(measure);
    }

    private MeasureResponse toResponse(FoodMeasure measure) {
        return new MeasureResponse(
                measure.getId(),
                measure.getName(),
                measure.getBaseAmount(),
                measure.isDefault()
        );
    }
}
