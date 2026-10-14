package com.fitness.food.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fitness.auth.security.UserPrincipal;
import com.fitness.common.exception.ConflictException;
import com.fitness.common.exception.ResourceNotFoundException;
import com.fitness.food.dto.CreateFoodRequest;
import com.fitness.food.dto.FoodResponse;
import com.fitness.food.dto.MeasureRequest;
import com.fitness.food.dto.UpdateFoodRequest;
import com.fitness.food.entity.Food;
import com.fitness.food.entity.FoodMeasure;
import com.fitness.food.entity.UnitType;
import com.fitness.food.repository.FoodRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class FoodServiceTest {

    @Mock
    private FoodRepository foodRepository;

    @InjectMocks
    private FoodService foodService;

    private final Long currentUserId = 10L;

    @BeforeEach
    void setUp() {
        UserPrincipal principal = new UserPrincipal(currentUserId, "testuser", "hashed", "Test", true, List.of());
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Create valid food without measures")
    void createFood_WithoutMeasures_Success() {
        CreateFoodRequest request = new CreateFoodRequest(
                "Arroz cocido", UnitType.G,
                new BigDecimal("130.00"), new BigDecimal("2.70"), new BigDecimal("28.20"),
                new BigDecimal("0.30"), null, null, null, "Cocido en agua", null
        );

        when(foodRepository.findByUserIdAndName(currentUserId, "Arroz cocido")).thenReturn(Optional.empty());
        when(foodRepository.save(any(Food.class))).thenAnswer(invocation -> {
            Food f = invocation.getArgument(0);
            f.setId(100L);
            return f;
        });

        FoodResponse response = foodService.createFood(request);

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals("Arroz cocido", response.name());
        assertEquals(UnitType.G, response.unitType());
        assertFalse(response.archived());
        assertTrue(response.measures().isEmpty());
        verify(foodRepository).save(any(Food.class));
    }

    @Test
    @DisplayName("Create valid food with measures: first measure becomes default when none is specified")
    void createFood_WithMeasures_FirstBecomesDefault() {
        MeasureRequest m1 = new MeasureRequest("Taza", new BigDecimal("150.00"), false);
        MeasureRequest m2 = new MeasureRequest("Plato", new BigDecimal("300.00"), false);
        CreateFoodRequest request = new CreateFoodRequest(
                "Arroz", UnitType.G,
                new BigDecimal("130.00"), new BigDecimal("2.70"), new BigDecimal("28.20"),
                null, null, null, null, null, List.of(m1, m2)
        );

        when(foodRepository.findByUserIdAndName(currentUserId, "Arroz")).thenReturn(Optional.empty());
        when(foodRepository.save(any(Food.class))).thenAnswer(invocation -> {
            Food f = invocation.getArgument(0);
            f.setId(101L);
            for (long i = 1; i <= f.getMeasures().size(); i++) {
                f.getMeasures().get((int) i - 1).setId(i);
            }
            return f;
        });

        FoodResponse response = foodService.createFood(request);

        assertNotNull(response);
        assertEquals(2, response.measures().size());
        assertEquals("Taza", response.measures().get(0).name());
        assertTrue(response.measures().get(0).isDefault(), "First measure should be default");
        assertEquals("Plato", response.measures().get(1).name());
        assertFalse(response.measures().get(1).isDefault(), "Second measure should not be default");
    }

    @Test
    @DisplayName("Create food with more than one default measure throws IllegalArgumentException")
    void createFood_MoreThanOneDefault_ThrowsException() {
        MeasureRequest m1 = new MeasureRequest("Taza", new BigDecimal("150.00"), true);
        MeasureRequest m2 = new MeasureRequest("Plato", new BigDecimal("300.00"), true);
        CreateFoodRequest request = new CreateFoodRequest(
                "Arroz", UnitType.G,
                new BigDecimal("130.00"), new BigDecimal("2.70"), new BigDecimal("28.20"),
                null, null, null, null, null, List.of(m1, m2)
        );

        when(foodRepository.findByUserIdAndName(currentUserId, "Arroz")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> foodService.createFood(request));
        assertEquals("Only one measure can be marked as default", ex.getMessage());
        verify(foodRepository, never()).save(any(Food.class));
    }

    @Test
    @DisplayName("Duplicate food name throws ConflictException")
    void createFood_DuplicateName_ThrowsConflictException() {
        CreateFoodRequest request = new CreateFoodRequest(
                "Huevo", UnitType.G,
                new BigDecimal("155.00"), new BigDecimal("12.60"), new BigDecimal("1.10"),
                null, null, null, null, null, null
        );

        Food existing = new Food();
        existing.setId(50L);
        existing.setName("Huevo");
        existing.setArchived(false);

        when(foodRepository.findByUserIdAndName(currentUserId, "Huevo")).thenReturn(Optional.of(existing));

        ConflictException ex = assertThrows(ConflictException.class, () -> foodService.createFood(request));
        assertEquals("A food with name 'Huevo' already exists.", ex.getMessage());
        verify(foodRepository, never()).save(any(Food.class));
    }

    @Test
    @DisplayName("Duplicate food name against archived food gives specific message suggesting unarchive")
    void createFood_DuplicateAgainstArchived_ThrowsSpecificConflictMessage() {
        CreateFoodRequest request = new CreateFoodRequest(
                "Huevo", UnitType.G,
                new BigDecimal("155.00"), new BigDecimal("12.60"), new BigDecimal("1.10"),
                null, null, null, null, null, null
        );

        Food existing = new Food();
        existing.setId(50L);
        existing.setName("Huevo");
        existing.setArchived(true);

        when(foodRepository.findByUserIdAndName(currentUserId, "Huevo")).thenReturn(Optional.of(existing));

        ConflictException ex = assertThrows(ConflictException.class, () -> foodService.createFood(request));
        assertTrue(ex.getMessage().contains("already exists in archived foods. Consider unarchiving it instead."));
        verify(foodRepository, never()).save(any(Food.class));
    }

    @Test
    @DisplayName("Get, update, archive and unarchive of food from ANOTHER user throws ResourceNotFoundException")
    void foodOperations_AnotherUserFood_ThrowsResourceNotFoundException() {
        Long foreignFoodId = 999L;
        when(foodRepository.findByIdAndUserIdWithMeasures(foreignFoodId, currentUserId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> foodService.getFood(foreignFoodId));

        UpdateFoodRequest updateRequest = new UpdateFoodRequest(
                "New Name", new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("20"),
                null, null, null, null, null
        );
        assertThrows(ResourceNotFoundException.class, () -> foodService.updateFood(foreignFoodId, updateRequest));

        assertThrows(ResourceNotFoundException.class, () -> foodService.archiveFood(foreignFoodId));

        assertThrows(ResourceNotFoundException.class, () -> foodService.unarchiveFood(foreignFoodId));
    }

    @Test
    @DisplayName("Update food does NOT change unitType and updates fields")
    void updateFood_DoesNotChangeUnitType_Success() {
        Long foodId = 1L;
        Food existing = new Food(
                currentUserId, "Leche", UnitType.ML,
                new BigDecimal("60.00"), new BigDecimal("3.20"), new BigDecimal("4.80"),
                new BigDecimal("3.00"), null, null, null, "Entera"
        );
        existing.setId(foodId);

        UpdateFoodRequest updateRequest = new UpdateFoodRequest(
                "Leche descremada",
                new BigDecimal("42.00"), new BigDecimal("3.40"), new BigDecimal("5.00"),
                new BigDecimal("0.10"), null, null, null, "0% grasa"
        );

        when(foodRepository.findByIdAndUserIdWithMeasures(foodId, currentUserId)).thenReturn(Optional.of(existing));
        when(foodRepository.findByUserIdAndName(currentUserId, "Leche descremada")).thenReturn(Optional.empty());
        when(foodRepository.save(any(Food.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FoodResponse response = foodService.updateFood(foodId, updateRequest);

        assertNotNull(response);
        assertEquals("Leche descremada", response.name());
        assertEquals(UnitType.ML, response.unitType(), "unitType must remain unchanged");
        assertEquals(new BigDecimal("42.00"), response.caloriesKcal());
        assertEquals("0% grasa", response.notes());
    }

    @Test
    @DisplayName("Archive and unarchive food are idempotent")
    void archiveAndUnarchive_Idempotent() {
        Long foodId = 1L;
        Food food = new Food();
        food.setId(foodId);
        food.setUserId(currentUserId);
        food.setArchived(false);

        when(foodRepository.findByIdAndUserIdWithMeasures(foodId, currentUserId)).thenReturn(Optional.of(food));
        when(foodRepository.save(any(Food.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FoodResponse archived = foodService.archiveFood(foodId);
        assertTrue(archived.archived());

        FoodResponse archivedAgain = foodService.archiveFood(foodId);
        assertTrue(archivedAgain.archived());

        FoodResponse unarchived = foodService.unarchiveFood(foodId);
        assertFalse(unarchived.archived());
    }

    @Test
    @DisplayName("getFoods filters by includeArchived and applies search query q")
    void getFoods_FiltersArchivedAndAppliesQ() {
        Food f1 = new Food(currentUserId, "Avena", UnitType.G, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, null, null, null, null, null);
        f1.setId(1L);

        when(foodRepository.findAllByUserIdWithFilters(eq(currentUserId), eq(false), eq("av")))
                .thenReturn(List.of(f1));

        List<FoodResponse> result = foodService.getFoods("av", false);

        assertEquals(1, result.size());
        assertEquals("Avena", result.get(0).name());
        verify(foodRepository).findAllByUserIdWithFilters(currentUserId, false, "av");
    }
}
