package com.hi.api.service;

import com.hi.api.exception.AiServiceBusyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class AiRequestAdmissionService {

    private final Semaphore totalSlots;
    private final Semaphore freeSlots;
    private final long freeWaitMs;
    private final long premiumWaitMs;

    public AiRequestAdmissionService(
            @Value("${app.ai.concurrent.total-slots:8}") int totalSlotCount,
            @Value("${app.ai.concurrent.free-slots:6}") int freeSlotCount,
            @Value("${app.ai.concurrent.free-wait-ms:250}") long freeWaitMs,
            @Value("${app.ai.concurrent.premium-wait-ms:1500}") long premiumWaitMs) {
        int safeTotal = Math.max(1, totalSlotCount);
        int safeFree = Math.max(1, Math.min(freeSlotCount, safeTotal));
        this.totalSlots = new Semaphore(safeTotal, true);
        this.freeSlots = new Semaphore(safeFree, true);
        this.freeWaitMs = Math.max(0, freeWaitMs);
        this.premiumWaitMs = Math.max(this.freeWaitMs, premiumWaitMs);
    }

    public <T> T execute(boolean premium, Supplier<T> action) {
        boolean freeAcquired = false;
        boolean totalAcquired = false;
        try {
            if (!premium) {
                freeAcquired = freeSlots.tryAcquire(freeWaitMs, TimeUnit.MILLISECONDS);
                if (!freeAcquired) throw new AiServiceBusyException();
            }
            totalAcquired = totalSlots.tryAcquire(
                    premium ? premiumWaitMs : freeWaitMs,
                    TimeUnit.MILLISECONDS
            );
            if (!totalAcquired) throw new AiServiceBusyException();
            return action.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiServiceBusyException();
        } finally {
            if (totalAcquired) totalSlots.release();
            if (freeAcquired) freeSlots.release();
        }
    }
}
