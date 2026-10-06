package com.deepblue.rescue.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.deepblue.rescue.domain.*;
import com.deepblue.rescue.dto.request.CreateTreatmentRequest;
import com.deepblue.rescue.exception.BusinessRuleException;
import com.deepblue.rescue.exception.ResourceNotFoundException;
import com.deepblue.rescue.mapper.TreatmentMapper;
import com.deepblue.rescue.repository.AnimalRepository;
import com.deepblue.rescue.repository.SpecialistRepository;
import com.deepblue.rescue.repository.TreatmentRepository;
import com.deepblue.rescue.service.impl.TreatmentServiceImpl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TreatmentServiceImplTest {

    @Mock
    TreatmentRepository repository;

    @Mock
    AnimalRepository animalRepository;

    @Mock
    SpecialistRepository specialistRepository;

    @Mock
    TreatmentMapper mapper;

    @InjectMocks
    TreatmentServiceImpl service;

    private final CreateTreatmentRequest request =
            new CreateTreatmentRequest(
                    "AN-001",
                    "SPEC-001",
                    LocalDateTime.of(2020, 8, 21, 9, 0),
                    TreatmentType.WOUND_CARE,
                    "Cleaning flipper injury");

    private void setup(
            RescueStatus status,
            boolean active,
            LocalDate rescueDate) {

        var animal = new Animal(
                "AN-001",
                "Turtle",
                "Chelonia mydas",
                AnimalSex.UNKNOWN);

        animal.setRescueCase(new RescueCase(
                "RES-001",
                rescueDate,
                "Bahia",
                status));

        when(animalRepository.findByAnimalCode("AN-001"))
                .thenReturn(Optional.of(animal));

        when(specialistRepository.findByProfessionalCode("SPEC-001"))
                .thenReturn(Optional.of(new Specialist(
                        "SPEC-001",
                        "Elena",
                        "Vargas",
                        "elena@example.com",
                        active)));
    }

    @Test
    void shouldRegisterTreatment() {
        setup(
                RescueStatus.IN_REHABILITATION,
                true,
                LocalDate.of(2020, 8, 20));

        when(repository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.register(request);

        var captor = ArgumentCaptor.forClass(Treatment.class);
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getAnimal().getAnimalCode())
                .isEqualTo("AN-001");
        assertThat(captor.getValue().getSpecialist().getProfessionalCode())
                .isEqualTo("SPEC-001");
        assertThat(captor.getValue().getPerformedAt())
                .isEqualTo(request.performedAt());

        verify(mapper).toResponse(captor.getValue());
    }

    @ParameterizedTest
    @EnumSource(
            value = RescueStatus.class,
            names = {"RELEASED", "CLOSED"})
    void shouldRejectTerminalCase(RescueStatus status) {
        setup(status, true, LocalDate.of(2020, 8, 20));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BusinessRuleException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectInactiveSpecialist() {
        setup(
                RescueStatus.IN_REHABILITATION,
                false,
                LocalDate.of(2020, 8, 20));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BusinessRuleException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectTreatmentBeforeRescue() {
        setup(
                RescueStatus.IN_REHABILITATION,
                true,
                LocalDate.of(2020, 8, 22));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Treatment date cannot be before rescue date");

        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectUnknownAnimal() {
        when(animalRepository.findByAnimalCode("AN-001"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(specialistRepository, repository);
    }

    @Test
    void shouldRejectUnknownSpecialist() {
        var animal = new Animal(
                "AN-001",
                "Turtle",
                "Chelonia mydas",
                AnimalSex.UNKNOWN);

        when(animalRepository.findByAnimalCode("AN-001"))
                .thenReturn(Optional.of(animal));

        when(specialistRepository.findByProfessionalCode("SPEC-001"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).save(any());
    }
}