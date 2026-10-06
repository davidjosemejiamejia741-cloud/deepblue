package com.deepblue.rescue.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.deepblue.rescue.domain.RescueCase;
import com.deepblue.rescue.domain.RescueStatus;
import com.deepblue.rescue.dto.request.ChangeRescueStatusRequest;
import com.deepblue.rescue.dto.response.RescueCaseResponse;
import com.deepblue.rescue.exception.BusinessRuleException;
import com.deepblue.rescue.exception.ResourceNotFoundException;
import com.deepblue.rescue.mapper.RescueCaseMapper;
import com.deepblue.rescue.repository.RescueCaseRepository;
import com.deepblue.rescue.service.impl.RescueCaseServiceImpl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RescueCaseServiceImplTest {

    @Mock
    RescueCaseRepository repository;

    @Mock
    RescueCaseMapper mapper;

    @InjectMocks
    RescueCaseServiceImpl service;

    @Test
    void shouldAdvanceToReadyForRelease() {
        var entity = new RescueCase(
                "RES-001",
                LocalDate.of(2020, 8, 20),
                "Bahia",
                RescueStatus.IN_REHABILITATION);

        when(repository.findByCaseCode("RES-001"))
                .thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);

        service.changeStatus(
                "RES-001",
                new ChangeRescueStatusRequest(
                        RescueStatus.READY_FOR_RELEASE));

        assertThat(entity.getStatus())
                .isEqualTo(RescueStatus.READY_FOR_RELEASE);

        verify(repository).save(entity);
        verify(mapper).toResponse(entity);
    }

    @Test
    void shouldRejectSkippedTransitionWithoutSaving() {
        var entity = new RescueCase(
                "RES-001",
                LocalDate.of(2020, 8, 20),
                "Bahia",
                RescueStatus.ADMITTED);

        when(repository.findByCaseCode("RES-001"))
                .thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.changeStatus(
                "RES-001",
                new ChangeRescueStatusRequest(RescueStatus.RELEASED)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Invalid status transition");

        assertThat(entity.getStatus()).isEqualTo(RescueStatus.ADMITTED);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectUnknownCase() {
        when(repository.findByCaseCode("RES-999"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(
                "RES-999",
                new ChangeRescueStatusRequest(RescueStatus.RELEASED)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).save(any());
    }

    @Test
void shouldFindCaseByCode() {
    var entity = new RescueCase(
            "RES-001",
            LocalDate.of(2020, 8, 20),
            "Bahia",
            RescueStatus.IN_REHABILITATION);

    var expected = new RescueCaseResponse(
            1L,
            "RES-001",
            LocalDate.of(2020, 8, 20),
            "Bahia",
            RescueStatus.IN_REHABILITATION,
            "DB-CAR",
            "AN-001");

    when(repository.findByCaseCode("RES-001"))
            .thenReturn(Optional.of(entity));
    when(mapper.toResponse(entity)).thenReturn(expected);

    var result = service.findByCode("RES-001");

    assertThat(result).isEqualTo(expected);
    verify(repository).findByCaseCode("RES-001");
    verify(mapper).toResponse(entity);
}

@Test
void shouldFindCasesByStatus() {
    var entity = new RescueCase(
            "RES-001",
            LocalDate.of(2020, 8, 20),
            "Bahia",
            RescueStatus.IN_REHABILITATION);

    var expected = new RescueCaseResponse(
            1L,
            "RES-001",
            LocalDate.of(2020, 8, 20),
            "Bahia",
            RescueStatus.IN_REHABILITATION,
            "DB-CAR",
            "AN-001");

    when(repository.findByStatusOrderByRescueDateAsc(
            RescueStatus.IN_REHABILITATION))
            .thenReturn(List.of(entity));

    when(mapper.toResponse(entity)).thenReturn(expected);

    var result = service.findByStatus(RescueStatus.IN_REHABILITATION);

    assertThat(result).containsExactly(expected);
    verify(repository).findByStatusOrderByRescueDateAsc(
            RescueStatus.IN_REHABILITATION);
    verify(mapper).toResponse(entity);
}
}