package com.deepblue.rescue.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.deepblue.rescue.domain.AnimalSex;
import com.deepblue.rescue.domain.TreatmentType;
import com.deepblue.rescue.dto.response.AnimalResponse;
import com.deepblue.rescue.dto.response.TreatmentResponse;
import com.deepblue.rescue.exception.GlobalExceptionHandler;
import com.deepblue.rescue.exception.ResourceNotFoundException;
import com.deepblue.rescue.service.AnimalService;
import com.deepblue.rescue.service.TreatmentService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnimalController.class)
@Import(GlobalExceptionHandler.class)
class AnimalControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AnimalService animalService;

    @MockitoBean
    TreatmentService treatmentService;

    private AnimalResponse animal(String code) {
        return new AnimalResponse(
                1L,
                code,
                "Green Sea Turtle",
                "Chelonia mydas",
                AnimalSex.UNKNOWN,
                null,
                "RES-001");
    }

    @Test
    void shouldReturnAnimalByCode() throws Exception {
        when(animalService.findByCode("AN-001"))
                .thenReturn(animal("AN-001"));

        mockMvc.perform(get("/api/animals/AN-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.animalCode").value("AN-001"));

        verify(animalService).findByCode("AN-001");
    }

    @Test
    void shouldReturnAnimalsInRehabilitation() throws Exception {
        when(animalService.findAnimalsInRehabilitation())
                .thenReturn(List.of(
                        animal("AN-001"),
                        animal("AN-002")));

        mockMvc.perform(get("/api/animals/in-rehabilitation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].animalCode").value("AN-002"));

        verify(animalService).findAnimalsInRehabilitation();
        verify(animalService, never()).findByCode(anyString());
    }

    @Test
    void shouldReturnAnimalTreatments() throws Exception {
        when(treatmentService.findByAnimalCode("AN-001"))
                .thenReturn(List.of(new TreatmentResponse(
                        1L,
                        "AN-001",
                        "SPEC-001",
                        LocalDateTime.of(2020, 8, 21, 9, 0),
                        TreatmentType.WOUND_CARE,
                        "Cleaning flipper injury")));

        mockMvc.perform(get("/api/animals/AN-001/treatments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].animalCode").value("AN-001"));

        verify(treatmentService).findByAnimalCode("AN-001");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldReturnTreatmentEligibility(boolean eligible)
            throws Exception {

        when(animalService.canReceiveTreatment("AN-001"))
                .thenReturn(eligible);

        mockMvc.perform(get("/api/animals/AN-001/treatment-eligibility"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.animalCode").value("AN-001"))
                .andExpect(jsonPath("$.eligible").value(eligible));

        verify(animalService).canReceiveTreatment("AN-001");
    }

    @Test
    void shouldReturn404ForUnknownAnimal() throws Exception {
        when(animalService.findByCode("AN-999"))
                .thenThrow(new ResourceNotFoundException(
                        "Animal not found: AN-999"));

        mockMvc.perform(get("/api/animals/AN-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Animal not found: AN-999"))
                .andExpect(jsonPath("$.details").isEmpty());

        verify(animalService).findByCode("AN-999");
    }

    @Test
    void shouldReturn404ForUnknownAnimalEligibility() throws Exception {
        when(animalService.canReceiveTreatment("AN-999"))
                .thenThrow(new ResourceNotFoundException(
                        "Animal not found: AN-999"));

        mockMvc.perform(get("/api/animals/AN-999/treatment-eligibility"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Animal not found: AN-999"))
                .andExpect(jsonPath("$.details").isEmpty());

        verify(animalService).canReceiveTreatment("AN-999");
    }
}
