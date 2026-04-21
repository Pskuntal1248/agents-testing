package com.opensre.byoc.controller;

import com.opensre.byoc.model.Facility;
import com.opensre.byoc.repository.FacilityRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/facilities")
public class FacilityController {
    private final FacilityRepository facilityRepository;

    public FacilityController(FacilityRepository facilityRepository) {
        this.facilityRepository = facilityRepository;
    }

    @GetMapping
    public List<Facility> getAll() {
        return facilityRepository.findAll();
    }

    @GetMapping("/{id}")
    public Facility getById(@PathVariable Long id) {
        return facilityRepository.findById(id).orElseThrow();
    }

    @PostMapping
    public Facility create(@RequestBody Facility facility) {
        return facilityRepository.save(facility);
    }

    @PutMapping("/{id}")
    public Facility update(@PathVariable Long id, @RequestBody Facility facility) {
        facility.setId(id);
        return facilityRepository.save(facility);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        facilityRepository.deleteById(id);
    }
}
