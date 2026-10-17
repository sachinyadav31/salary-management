package com.acme.salary.insights;

import com.acme.salary.employee.WorkforceChanged;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Caches the workforce snapshot until a write commits.
 * <ul>
 *   <li><b>Invalidate after commit</b>: evicting earlier would let a concurrent reader reload the
 *       pre-commit data and cache it indefinitely.</li>
 *   <li><b>Generation counter</b>: each cached value remembers the generation it was loaded in. If an
 *       invalidation happens while a load is in flight, the loaded value is already stale and is ignored
 *       on the next read instead of being served.</li>
 * </ul>
 * Single instance, one cached object: an AtomicReference is the simplest correct tool. Several app instances
 * would need a shared cache or a broadcast invalidation.
 */
@Component
public class WorkforceSnapshotCache {

    private record Cached(long generation, WorkforceSnapshot snapshot) {
    }

    private final Supplier<WorkforceSnapshot> loader;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<Cached> cached = new AtomicReference<>();

    @Autowired
    public WorkforceSnapshotCache(WorkforceSnapshotLoader loader) {
        this(loader::load);
    }

    WorkforceSnapshotCache(Supplier<WorkforceSnapshot> loader) {
        this.loader = loader;
    }

    public WorkforceSnapshot get() {
        long current = generation.get();
        Cached hit = cached.get();
        if (hit != null && hit.generation() == current) {
            return hit.snapshot();
        }
        WorkforceSnapshot fresh = loader.get();
        // Keep whichever entry is newest; a slow, older load never replaces a newer one.
        cached.accumulateAndGet(new Cached(current, fresh),
                (existing, candidate) -> existing != null && existing.generation() > candidate.generation()
                        ? existing : candidate);
        return fresh;
    }

    public void invalidate() {
        generation.incrementAndGet();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onWorkforceChanged(WorkforceChanged event) {
        invalidate();
    }
}
