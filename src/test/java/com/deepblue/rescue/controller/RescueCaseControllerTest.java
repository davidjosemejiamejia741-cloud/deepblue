package com.deepblue.rescue.controller;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.deepblue.rescue.domain.RescueStatus;
import com.deepblue.rescue.dto.request.ChangeRescueStatusRequest;
import com.deepblue.rescue.dto.response.RescueCaseResponse;
import com.deepblue.rescue.exception.*;
import com.deepblue.rescue.service.RescueCaseService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RescueCaseController.class)
@Import(GlobalExceptionHandler.class)
class RescueCaseControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RescueCaseService service;

    private RescueCaseResponse response(RescueStatus status) {
        return new RescueCaseResponse(
                1L,
                "RES-001",
                LocalDate.of(2026, 8, 20),
                "Bahia Concha",
                status,
                "DB-CAR",
                "AN-001");
    }

    @Test
    void shouldReturnRescueCaseByCode() throws Exception {
        when(service.findByCode("RES-001"))
                .thenReturn(response(RescueStatus.IN_REHABILITATION));

        mockMvc.perform(get("/api/rescue-cases/RES-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseCode").value("RES-001"))
                .andExpect(jsonPath("$.status").value("IN_REHABILITATION"));

        verify(service).findByCode("RES-001");
    }

    @Test
    void shouldReturn404WhenCaseDoesNotExist() throws Exception {
        when(service.findByCode("RES-999"))
                .thenThrow(new ResourceNotFoundException(
                        "Rescue case not found: RES-999"));

        mockMvc.perform(get("/api/rescue-cases/RES-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Rescue case not found: RES-999"))
                .andExpect(jsonPath("$.details").isEmpty());

        verify(service).findByCode("RES-999");
    }

    @Test
    void shouldReturnCasesByStatus() throws Exception {
        when(service.findByStatus(RescueStatus.IN_REHABILITATION))
                .thenReturn(List.of(
                        response(RescueStatus.IN_REHABILITATION),
                        response(RescueStatus.IN_REHABILITATION)));

        mockMvc.perform(get("/api/rescue-cases")
                        .param("status", "IN_REHABILITATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status")
                        .value("IN_REHABILITATION"));

        verify(service).findByStatus(RescueStatus.IN_REHABILITATION);
    }

    @Test
    void shouldRejectInvalidQueryStatus() throws Exception {
        mockMvc.perform(get("/api/rescue-cases")
                        .param("status", "FLYING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("Invalid request parameter"))
                .andExpect(jsonPath("$.details.status")
                        .value("Invalid value: FLYING"));

        verify(service, never()).findByStatus(any());
    }

    @Test
    void shouldRejectMissingQueryStatus() throws Exception {
        mockMvc.perform(get("/api/rescue-cases"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.status").exists());

        verifyNoInteractions(service);
    }

    @Test
    void shouldChangeStatus() throws Exception {
        var request = new ChangeRescueStatusRequest(
                RescueStatus.READY_FOR_RELEASE);

        when(service.changeStatus("RES-001", request))
                .thenReturn(response(RescueStatus.READY_FOR_RELEASE));

        mockMvc.perform(patch("/api/rescue-cases/RES-001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"READY_FOR_RELEASE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_RELEASE"));

        verify(service).changeStatus("RES-001", request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}"})
    void shouldRejectMissingStatus(String body) throws Exception {
        mockMvc.perform(patch("/api/rescue-cases/RES-001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("Request validation failed"))
                .andExpect(jsonPath("$.details.status")
                        .value("Status is required"));

        verify(service, never()).changeStatus(anyString(), any());
    }

    @Test
    void shouldRejectInvalidTransition() throws Exception {
        when(service.changeStatus(eq("RES-001"), any()))
                .thenThrow(new BusinessRuleException(
                        "Invalid status transition"));

        mockMvc.perform(patch("/api/rescue-cases/RES-001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"RELEASED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("Invalid status transition"))
                .andExpect(jsonPath("$.details").isEmpty());

        verify(service).changeStatus(
                "RES-001",
                new ChangeRescueStatusRequest(RescueStatus.RELEASED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"status\":\"FLYING\"}", "{", ""})
    void shouldRejectInvalidJson(String body) throws Exception {
        mockMvc.perform(patch("/api/rescue-cases/RES-001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("Malformed or invalid JSON request"))
                .andExpect(jsonPath("$.details.body")
                        .value("Check JSON syntax and enum values"));

        verify(service, never()).changeStatus(anyString(), any());
    }

    @Test
    void shouldHideUnexpectedErrorDetails() throws Exception {
        when(service.findByCode("RES-001"))
                .thenThrow(new RuntimeException("SQL password secret"));

        mockMvc.perform(get("/api/rescue-cases/RES-001"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error")
                        .value("Internal Server Error"))
                .andExpect(jsonPath("$.message")
                        .value("An unexpected error occurred"))
                .andExpect(jsonPath("$.details").isEmpty());

        verify(service).findByCode("RES-001");
    }
}
