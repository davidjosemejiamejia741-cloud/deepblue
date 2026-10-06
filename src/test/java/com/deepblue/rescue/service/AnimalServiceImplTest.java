package com.deepblue.rescue.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import com.deepblue.rescue.dto.response.AnimalResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.deepblue.rescue.domain.Animal;
import com.deepblue.rescue.domain.AnimalSex;
import com.deepblue.rescue.domain.RescueCase;
import com.deepblue.rescue.domain.RescueStatus;
import com.deepblue.rescue.exception.ResourceNotFoundException;
import com.deepblue.rescue.mapper.AnimalMapper;
import com.deepblue.rescue.repository.AnimalRepository;
import com.deepblue.rescue.service.impl.AnimalServiceImpl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnimalServiceImplTest {

    @Mock
    AnimalRepository repository;

    @Mock
    AnimalMapper mapper;

    @InjectMocks
    AnimalServiceImpl service;

    @ParameterizedTest
    @EnumSource(RescueStatus.class)
    void eligibilityReflectsCaseStatus(RescueStatus status) {
        var animal = new Animal(
                "AN-001",
                "Turtle",
                "Chelonia mydas",
                AnimalSex.UNKNOWN);

        animal.setRescueCase(new RescueCase(
                "RES-001",
                LocalDate.of(2020, 8, 20),
                "Bahia",
                status));

        when(repository.findByAnimalCode("AN-001"))
                .thenReturn(Optional.of(animal));

        assertThat(service.canReceiveTreatment("AN-001"))
                .isEqualTo(
                        status != RescueStatus.RELEASED
                        && status != RescueStatus.CLOSED);
    }

    @Test
    void unknownAnimalThrowsNotFound() {
        when(repository.findByAnimalCode("AN-999"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.canReceiveTreatment("AN-999"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Animal not found: AN-999");
    }

    @Test
void shouldFindAnimalByCode() {
    var entity = new Animal(
            "AN-001",
            "Turtle",
            "Chelonia mydas",
            AnimalSex.UNKNOWN);

    var expected = new AnimalResponse(
            1L,
            "AN-001",
            "Turtle",
            "Chelonia mydas",
            AnimalSex.UNKNOWN,
            null,
            "RES-001");

    when(repository.findByAnimalCode("AN-001"))
            .thenReturn(Optional.of(entity));
    when(mapper.toResponse(entity)).thenReturn(expected);

    var result = service.findByCode("AN-001");

    assertThat(result).isEqualTo(expected);
    verify(repository).findByAnimalCode("AN-001");
    verify(mapper).toResponse(entity);
}

@Test
void shouldFindAnimalsInRehabilitation() {
    var entity = new Animal(
            "AN-001",
            "Turtle",
            "Chelonia mydas",
            AnimalSex.UNKNOWN);

    var expected = new AnimalResponse(
            1L,
            "AN-001",
            "Turtle",
            "Chelonia mydas",
            AnimalSex.UNKNOWN,
            null,
            "RES-001");

    when(repository.findByRescueCaseStatus(
            RescueStatus.IN_REHABILITATION))
            .thenReturn(List.of(entity));

    when(mapper.toResponse(entity)).thenReturn(expected);

    var result = service.findAnimalsInRehabilitation();

    assertThat(result).containsExactly(expected);
    verify(repository).findByRescueCaseStatus(
            RescueStatus.IN_REHABILITATION);
    verify(mapper).toResponse(entity);
}
}
