package com.hi.api.service;

import com.hi.api.dto.request.CreateCycleRequest;
import com.hi.api.dto.request.UpdateCycleRequest;
import com.hi.api.model.Cycle;
import com.hi.api.repository.CycleRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class CycleService {

    private final CycleRepository cycleRepository;

    public CycleService(CycleRepository cycleRepository) {
        this.cycleRepository = cycleRepository;
    }

    public List<Cycle> getCycles(String userId) {
        return cycleRepository.findByUserIdOrderByStartDateDesc(userId);
    }

    public Cycle createCycle(String userId, CreateCycleRequest req) {
        Cycle cycle = new Cycle();
        cycle.setUserId(userId);
        cycle.setStartDate(req.getStartDate());
        cycle.setEndDate(req.getEndDate());
        cycle.setCycleLength(req.getCycleLength() != null ? req.getCycleLength() : 28);
        cycle.setNotes(req.getNotes() != null ? req.getNotes() : "");

        if (req.getEndDate() != null && !req.getEndDate().isBlank()) {
            try {
                LocalDate start = LocalDate.parse(req.getStartDate().substring(0, 10));
                LocalDate end = LocalDate.parse(req.getEndDate().substring(0, 10));
                int days = (int) ChronoUnit.DAYS.between(start, end) + 1;
                cycle.setPeriodLength(days);
            } catch (Exception ignored) {
                // ignore parse errors
            }
        }

        return cycleRepository.save(cycle);
    }

    public Cycle updateCycle(String userId, String cycleId, UpdateCycleRequest req) {
        Cycle cycle = cycleRepository.findByIdAndUserId(cycleId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chu kỳ"));

        if (req.getStartDate() != null) cycle.setStartDate(req.getStartDate());
        if (req.getEndDate() != null) cycle.setEndDate(req.getEndDate());
        if (req.getCycleLength() != null) cycle.setCycleLength(req.getCycleLength());
        if (req.getPeriodLength() != null) cycle.setPeriodLength(req.getPeriodLength());
        if (req.getNotes() != null) cycle.setNotes(req.getNotes());

        return cycleRepository.save(cycle);
    }

    public void deleteCycle(String userId, String cycleId) {
        Cycle cycle = cycleRepository.findByIdAndUserId(cycleId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chu kỳ"));
        cycleRepository.delete(cycle);
    }
}
