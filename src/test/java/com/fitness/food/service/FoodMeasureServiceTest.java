package com.fitness.food.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fitness.auth.security.UserPrincipal;
import com.fitness.common.exception.ConflictException;
import com.fitness.common.exception.ResourceNotFoundException;
import com.fitness.food.dto.MeasureRequest;
import com.fitness.food.dto.MeasureResponse;
import com.fitness.food.entity.Food;
import com.fitness.food.entity.FoodMeasure;
import com.fitness.food.repository.FoodMeasureRepository;
import com.fitness.food.repository.FoodRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
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
class FoodMeasureServiceTest {

    @Mock
    private FoodRepository foodRepository;

    @Mock
    private FoodMeasureRepository foodMeasureRepository;

    @InjectMocks
    private FoodMeasureService foodMeasureService;

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
    @DisplayName("Create first measure becomes default automatically even if request says false")
    void createMeasure_FirstMeasure_BecomesDefaultAutomatically() {
        Long foodId = 1L;
        Food food = new Food();
        food.setId(foodId);
        food.setUserId(currentUserId);

        MeasureRequest request = new MeasureRequest("Unidad", new BigDecimal("50.00"), false);

        when(foodRepository.findByIdAndUserId(foodId, currentUserId)).thenReturn(Optional.of(food));
        when(foodMeasureRepository.findByFoodIdAndName(foodId, "Unidad")).thenReturn(Optional.empty());
        when(foodMeasureRepository.findAllByFoodId(foodId)).thenReturn(new ArrayList<>());
        when(foodMeasureRepository.save(any(FoodMeasure.class))).thenAnswer(invocation -> {
            FoodMeasure m = invocation.getArgument(0);
            m.setId(10L);
            return m;
        });

        MeasureResponse response = foodMeasureService.createMeasure(foodId, request);

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals("Unidad", response.name());
        assertTrue(response.isDefault(), "First measure must be set to default automatically");
    }

    @Test
    @DisplayName("Create new measure with isDefault=true unmarks existing default measure")
    void createMeasure_MarkAsDefault_UnmarksPreviousDefault() {
        Long foodId = 1L;
        Food food = new Food();
        food.setId(foodId);
        food.setUserId(currentUserId);

        FoodMeasure existingDefault = new FoodMeasure("Unidad", new BigDecimal("50.00"), true);
        existingDefault.setId(10L);

        MeasureRequest request = new MeasureRequest("Porcion grande", new BigDecimal("100.00"), true);

        when(foodRepository.findByIdAndUserId(foodId, currentUserId)).thenReturn(Optional.of(food));
        when(foodMeasureRepository.findByFoodIdAndName(foodId, "Porcion grande")).thenReturn(Optional.empty());
        when(foodMeasureRepository.findAllByFoodId(foodId)).thenReturn(List.of(existingDefault));
        when(foodMeasureRepository.save(any(FoodMeasure.class))).thenAnswer(invocation -> {
            FoodMeasure m = invocation.getArgument(0);
            if (m.getId() == null) {
                m.setId(11L);
            }
            return m;
        });

        MeasureResponse response = foodMeasureService.createMeasure(foodId, request);

        assertNotNull(response);
        assertTrue(response.isDefault(), "New measure should be default");
        assertFalse(existingDefault.isDefault(), "Previous default measure must be unmarked");
        verify(foodMeasureRepository).save(existingDefault);
    }

    @Test
    @DisplayName("Create measure with duplicate name throws ConflictException")
    void createMeasure_DuplicateName_ThrowsConflictException() {
        Long foodId = 1L;
        Food food = new Food();
        food.setId(foodId);
        food.setUserId(currentUserId);

        FoodMeasure existing = new FoodMeasure("Unidad", new BigDecimal("50.00"), true);
        existing.setId(10L);

        MeasureRequest request = new MeasureRequest("Unidad", new BigDecimal("55.00"), false);

        when(foodRepository.findByIdAndUserId(foodId, currentUserId)).thenReturn(Optional.of(food));
        when(foodMeasureRepository.findByFoodIdAndName(foodId, "Unidad")).thenReturn(Optional.of(existing));

        ConflictException ex = assertThrows(ConflictException.class, () -> foodMeasureService.createMeasure(foodId, request));
        assertEquals("A measure with name 'Unidad' already exists for this food.", ex.getMessage());
        verify(foodMeasureRepository, never()).save(any(FoodMeasure.class));
    }

    @Test
    @DisplayName("Operating on measures of a food belonging to ANOTHER user throws ResourceNotFoundException")
    void measureOperations_ForeignFood_ThrowsResourceNotFoundException() {
        Long foreignFoodId = 999L;
        when(foodRepository.findByIdAndUserId(foreignFoodId, currentUserId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> foodMeasureService.getMeasures(foreignFoodId));

        MeasureRequest request = new MeasureRequest("Taza", new BigDecimal("200.00"), false);
        assertThrows(ResourceNotFoundException.class, () -> foodMeasureService.createMeasure(foreignFoodId, request));

        assertThrows(ResourceNotFoundException.class, () -> foodMeasureService.updateMeasure(foreignFoodId, 1L, request));

        assertThrows(ResourceNotFoundException.class, () -> foodMeasureService.deleteMeasure(foreignFoodId, 1L));
    }

    @Test
    @DisplayName("Update measure to isDefault=true unmarks other measures")
    void updateMeasure_SetAsDefault_UnmarksOthers() {
        Long foodId = 1L;
        Food food = new Food();
        food.setId(foodId);
        food.setUserId(currentUserId);

        FoodMeasure m1 = new FoodMeasure("M1", new BigDecimal("50.00"), true);
        m1.setId(10L);
        FoodMeasure m2 = new FoodMeasure("M2", new BigDecimal("100.00"), false);
        m2.setId(20L);

        MeasureRequest request = new MeasureRequest("M2 Updated", new BigDecimal("120.00"), true);

        when(foodRepository.findByIdAndUserId(foodId, currentUserId)).thenReturn(Optional.of(food));
        when(foodMeasureRepository.findByIdAndFoodId(20L, foodId)).thenReturn(Optional.of(m2));
        when(foodMeasureRepository.findByFoodIdAndName(foodId, "M2 Updated")).thenReturn(Optional.empty());
        when(foodMeasureRepository.findAllByFoodId(foodId)).thenReturn(List.of(m1, m2));
        when(foodMeasureRepository.save(any(FoodMeasure.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MeasureResponse response = foodMeasureService.updateMeasure(foodId, 20L, request);

        assertTrue(response.isDefault(), "M2 should now be default");
        assertFalse(m1.isDefault(), "M1 should no longer be default");
    }

    @Test
    @DisplayName("Delete default measure leaves food without default without promoting another")
    void deleteMeasure_DefaultMeasure_LeavesFoodWithoutDefault() {
        Long foodId = 1L;
        Food food = new Food();
        food.setId(foodId);
        food.setUserId(currentUserId);

        FoodMeasure defaultMeasure = new FoodMeasure("Default", new BigDecimal("50.00"), true);
        defaultMeasure.setId(10L);

        when(foodRepository.findByIdAndUserId(foodId, currentUserId)).thenReturn(Optional.of(food));
        when(foodMeasureRepository.findByIdAndFoodId(10L, foodId)).thenReturn(Optional.of(defaultMeasure));

        foodMeasureService.deleteMeasure(foodId, 10L);

        verify(foodMeasureRepository).delete(defaultMeasure);
    }
}
