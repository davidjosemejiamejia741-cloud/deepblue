package com.deepblue.rescue.controller;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.deepblue.rescue.domain.TreatmentType;
import com.deepblue.rescue.dto.request.CreateTreatmentRequest;
import com.deepblue.rescue.dto.response.TreatmentResponse;
import com.deepblue.rescue.exception.*;
import com.deepblue.rescue.service.TreatmentService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TreatmentController.class)
@Import(GlobalExceptionHandler.class)
class TreatmentControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TreatmentService service;

    private static final String VALID = """
            {
              "animalCode": "AN-001",
              "specialistCode": "SPEC-001",
              "performedAt": "2020-08-21T09:00:00",
              "type": "WOUND_CARE",
              "description": "Cleaning and treatment of flipper injury."
            }
            """;

    @Test
    void shouldCreateTreatment() throws Exception {
        var request = new CreateTreatmentRequest(
                "AN-001",
                "SPEC-001",
                LocalDateTime.of(2020, 8, 21, 9, 0),
                TreatmentType.WOUND_CARE,
                "Cleaning and treatment of flipper injury.");

        when(service.register(request))
                .thenReturn(new TreatmentResponse(
                        100L,
                        request.animalCode(),
                        request.specialistCode(),
                        request.performedAt(),
                        request.type(),
                        request.description()));

        mockMvc.perform(post("/api/treatments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.animalCode").value("AN-001"))
                .andExpect(jsonPath("$.specialistCode").value("SPEC-001"))
                .andExpect(jsonPath("$.type").value("WOUND_CARE"));

        verify(service).register(request);
    }

    @Test
    void shouldRejectInvalidTreatment() throws Exception {
        mockMvc.perform(post("/api/treatments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "animalCode": "",
                                  "specialistCode": "",
                                  "type": null,
                                  "description": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Request validation failed"))
                .andExpect(jsonPath("$.details.animalCode")
                        .value("Animal code is required"))
                .andExpect(jsonPath("$.details.specialistCode")
                        .value("Specialist code is required"))
                .andExpect(jsonPath("$.details.performedAt")
                        .value("Treatment date is required"))
                .andExpect(jsonPath("$.details.type")
                        .value("Treatment type is required"))
                .andExpect(jsonPath("$.details.description").exists());

        verify(service, never()).register(any());
    }

    @Test
    void shouldReturn404ForUnknownAnimal() throws Exception {
        when(service.register(any()))
                .thenThrow(new ResourceNotFoundException(
                        "Animal not found: AN-999"));

        mockMvc.perform(post("/api/treatments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID.replace("AN-001", "AN-999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Animal not found: AN-999"))
                .andExpect(jsonPath("$.details").isEmpty());

        verify(service).register(any(CreateTreatmentRequest.class));
    }

    @Test
    void shouldReturn409ForBusinessRule() throws Exception {
        when(service.register(any()))
                .thenThrow(new BusinessRuleException(
                        "Released animals cannot receive treatments"));

        mockMvc.perform(post("/api/treatments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("Released animals cannot receive treatments"))
                .andExpect(jsonPath("$.details").isEmpty());

        verify(service).register(any(CreateTreatmentRequest.class));
    }

    @Test
    void shouldRejectFutureDate() throws Exception {
        String future = LocalDateTime.now()
                .plusDays(2)
                .withNano(0)
                .toString();

        mockMvc.perform(post("/api/treatments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID.replace(
                                "2020-08-21T09:00:00",
                                future)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.performedAt")
                        .value("Treatment date cannot be in the future"));

        verify(service, never()).register(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {9, 501})
    void shouldRejectDescriptionOutsideBounds(int length)
            throws Exception {

        mockMvc.perform(post("/api/treatments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID.replace(
                                "Cleaning and treatment of flipper injury.",
                                "a".repeat(length))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.description")
                        .value(
                                "Description must contain between 10 and 500 characters"));

        verify(service, never()).register(any());
    }
}