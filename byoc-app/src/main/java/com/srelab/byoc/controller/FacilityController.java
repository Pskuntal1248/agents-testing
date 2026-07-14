package com.srelab.byoc.controller;

import com.srelab.byoc.model.Facility;
import com.srelab.byoc.repository.FacilityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/facilities")
public class FacilityController {
    private final FacilityRepository facilityRepository;

    /**
     * Toggles a deliberately-injected N+1 query bug: when true, getAll()
     * fetches ids first and then issues one query per row instead of a
     * single batch query, mirroring a real-world "loop that queries inside
     * the loop" performance bug. Controlled via env var / Spring profile
     * property so the fault injector can flip it without shipping a second
     * build of the app.
     */
    @Value("${srelab.fault.n1-query-mode:false}")
    private boolean n1QueryModeEnabled;

    public FacilityController(FacilityRepository facilityRepository) {
        this.facilityRepository = facilityRepository;
    }

    @GetMapping
    public List<Facility> getAll() {
        if (n1QueryModeEnabled) {
            return getAllWithN1Bug();
        }
        return facilityRepository.findAll();
    }

    private List<Facility> getAllWithN1Bug() {
        List<Long> ids = facilityRepository.findAll().stream().map(Facility::getId).toList();
        List<Facility> result = new ArrayList<>();
        for (Long id : ids) {
            // Deliberate N+1: one round trip per row instead of a single batch fetch.
            facilityRepository.findById(id).ifPresent(result::add);
        }
        return result;
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
