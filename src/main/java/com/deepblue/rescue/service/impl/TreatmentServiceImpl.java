package com.deepblue.rescue.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deepblue.rescue.domain.*;
import com.deepblue.rescue.dto.request.CreateTreatmentRequest;
import com.deepblue.rescue.dto.response.TreatmentResponse;
import com.deepblue.rescue.exception.*;
import com.deepblue.rescue.mapper.TreatmentMapper;
import com.deepblue.rescue.repository.*;
import com.deepblue.rescue.service.TreatmentService;

@Service
@Transactional(readOnly = true)
public class TreatmentServiceImpl implements TreatmentService {

    private final TreatmentRepository repository;
    private final AnimalRepository animalRepository;
    private final SpecialistRepository specialistRepository;
    private final TreatmentMapper mapper;

    public TreatmentServiceImpl(
            TreatmentRepository repository,
            AnimalRepository animalRepository,
            SpecialistRepository specialistRepository,
            TreatmentMapper mapper) {
        this.repository = repository;
        this.animalRepository = animalRepository;
        this.specialistRepository = specialistRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TreatmentResponse register(CreateTreatmentRequest request) {
        var animal = animalRepository
                .findByAnimalCode(request.animalCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Animal not found: " + request.animalCode()));

        var specialist = specialistRepository
                .findByProfessionalCode(request.specialistCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Specialist not found: " + request.specialistCode()));

        if (!specialist.isActive()) {
            throw new BusinessRuleException(
                    "Inactive specialists cannot perform treatments");
        }

        var rescueCase = animal.getRescueCase();

        if (rescueCase.getStatus() == RescueStatus.RELEASED) {
            throw new BusinessRuleException(
                    "Released animals cannot receive treatments");
        }

        if (rescueCase.getStatus() == RescueStatus.CLOSED) {
            throw new BusinessRuleException(
                    "Animals with closed cases cannot receive treatments");
        }

        if (request.performedAt().toLocalDate()
                .isBefore(rescueCase.getRescueDate())) {
            throw new BusinessRuleException(
                    "Treatment date cannot be before rescue date");
        }

        var treatment = new Treatment(
                animal,
                specialist,
                request.performedAt(),
                request.type(),
                request.description());

        return mapper.toResponse(repository.save(treatment));
    }

    @Override
    public List<TreatmentResponse> findByAnimalCode(String animalCode) {
        var animal = animalRepository.findByAnimalCode(animalCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Animal not found: " + animalCode));

        return repository
                .findByAnimalIdOrderByPerformedAtAsc(animal.getId())
                .stream()
                .map(mapper::toResponse)
                .toList();
    }
}