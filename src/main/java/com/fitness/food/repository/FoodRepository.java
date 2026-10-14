package com.fitness.food.repository;

import com.fitness.food.entity.Food;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FoodRepository extends JpaRepository<Food, Long> {

    Optional<Food> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT f FROM Food f LEFT JOIN FETCH f.measures WHERE f.id = :id AND f.userId = :userId")
    Optional<Food> findByIdAndUserIdWithMeasures(@Param("id") Long id, @Param("userId") Long userId);

    Optional<Food> findByUserIdAndName(Long userId, String name);

    @Query("""
            SELECT DISTINCT f FROM Food f
            LEFT JOIN FETCH f.measures
            WHERE f.userId = :userId
              AND (:includeArchived = true OR f.archived = false)
              AND (:q IS NULL OR :q = '' OR f.name LIKE CONCAT('%', :q, '%'))
            ORDER BY f.name ASC
            """)
    List<Food> findAllByUserIdWithFilters(@Param("userId") Long userId,
                                         @Param("includeArchived") boolean includeArchived,
                                         @Param("q") String q);
}
