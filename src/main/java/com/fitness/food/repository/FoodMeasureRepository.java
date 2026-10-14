package com.fitness.food.repository;

import com.fitness.food.entity.FoodMeasure;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FoodMeasureRepository extends JpaRepository<FoodMeasure, Long> {

    List<FoodMeasure> findAllByFoodId(Long foodId);

    Optional<FoodMeasure> findByIdAndFoodId(Long id, Long foodId);

    Optional<FoodMeasure> findByFoodIdAndName(Long foodId, String name);
}
