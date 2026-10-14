package com.fitness.food.controller;

import com.fitness.food.dto.CreateFoodRequest;
import com.fitness.food.dto.FoodResponse;
import com.fitness.food.dto.UpdateFoodRequest;
import com.fitness.food.service.FoodService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/foods")
public class FoodController {

    private final FoodService foodService;

    public FoodController(FoodService foodService) {
        this.foodService = foodService;
    }

    @GetMapping
    public ResponseEntity<List<FoodResponse>> getFoods(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        List<FoodResponse> foods = foodService.getFoods(q, includeArchived);
        return ResponseEntity.ok(foods);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FoodResponse> getFood(@PathVariable Long id) {
        FoodResponse food = foodService.getFood(id);
        return ResponseEntity.ok(food);
    }

    @PostMapping
    public ResponseEntity<FoodResponse> createFood(@Valid @RequestBody CreateFoodRequest request) {
        FoodResponse created = foodService.createFood(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FoodResponse> updateFood(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFoodRequest request) {
        FoodResponse updated = foodService.updateFood(id, request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<FoodResponse> archiveFood(@PathVariable Long id) {
        FoodResponse archived = foodService.archiveFood(id);
        return ResponseEntity.ok(archived);
    }

    @PostMapping("/{id}/unarchive")
    public ResponseEntity<FoodResponse> unarchiveFood(@PathVariable Long id) {
        FoodResponse unarchived = foodService.unarchiveFood(id);
        return ResponseEntity.ok(unarchived);
    }
}
