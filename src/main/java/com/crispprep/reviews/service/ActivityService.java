package com.crispprep.reviews.service;

import com.crispprep.reviews.entity.ActivityEvent;
import com.crispprep.reviews.repository.ActivityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/** Writes the activity log, and keeps it to the last 12 months. */
@Service
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);
    public static final Duration KEEP = Duration.ofDays(365);

    private final ActivityRepository events;

    public ActivityService(ActivityRepository events) {
        this.events = events;
    }

    /** Never lets a logging problem break the user's request. */
    public void record(ActivityEvent.Type type, Long userId, String pdfSlug, String detail) {
        try {
            events.save(new ActivityEvent(type, userId, pdfSlug, detail));
        } catch (RuntimeException e) {
            log.warn("Could not record {} event: {}", type, e.getMessage());
        }
    }

    /** Daily at 03:30 IST, and at every start (the free server sleeps, so the daily run can be missed). */
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kolkata")
    @EventListener(ApplicationReadyEvent.class)
    public void deleteOld() {
        int n = events.deleteOlderThan(Instant.now().minus(KEEP));
        if (n > 0) log.info("Deleted {} activity events older than 12 months", n);
    }
}
