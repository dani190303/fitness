package com.fitness.food.controller;

import com.fitness.food.dto.MeasureRequest;
import com.fitness.food.dto.MeasureResponse;
import com.fitness.food.service.FoodMeasureService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/foods/{foodId}/measures")
public class FoodMeasureController {

    private final FoodMeasureService foodMeasureService;

    public FoodMeasureController(FoodMeasureService foodMeasureService) {
        this.foodMeasureService = foodMeasureService;
    }

    @GetMapping
    public ResponseEntity<List<MeasureResponse>> getMeasures(@PathVariable Long foodId) {
        List<MeasureResponse> measures = foodMeasureService.getMeasures(foodId);
        return ResponseEntity.ok(measures);
    }

    @PostMapping
    public ResponseEntity<MeasureResponse> createMeasure(
            @PathVariable Long foodId,
            @Valid @RequestBody MeasureRequest request) {
        MeasureResponse created = foodMeasureService.createMeasure(foodId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{measureId}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{measureId}")
    public ResponseEntity<MeasureResponse> updateMeasure(
            @PathVariable Long foodId,
            @PathVariable Long measureId,
            @Valid @RequestBody MeasureRequest request) {
        MeasureResponse updated = foodMeasureService.updateMeasure(foodId, measureId, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{measureId}")
    public ResponseEntity<Void> deleteMeasure(
            @PathVariable Long foodId,
            @PathVariable Long measureId) {
        foodMeasureService.deleteMeasure(foodId, measureId);
        return ResponseEntity.noContent().build();
    }
}
