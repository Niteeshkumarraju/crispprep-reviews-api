package com.crispprep.reviews.service;

import com.crispprep.reviews.entity.User;
import com.crispprep.reviews.entity.UserProgress;
import com.crispprep.reviews.repository.UserProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * A "study day" (IST) counts when a signed-in user reads or downloads a PDF or finishes the Daily Quiz.
 * Consecutive days build the streak; one missed day is forgiven if the user has a freeze. Streak milestones unlock books.
 */
@Service
public class StreakService {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public record Result(boolean newDay, int streak, boolean freezeUsed, List<String> unlocked) { }

    private final UserProgressRepository progress;
    private final RewardService rewards;

    public StreakService(UserProgressRepository progress, RewardService rewards) {
        this.progress = progress;
        this.rewards = rewards;
    }

    public static LocalDate today() { return LocalDate.now(IST); }

    /** The streak to SHOW today: still alive if the user studied today or yesterday (or the day before, with a freeze). */
    public static int shown(UserProgress p) {
        if (p.getLastDay() == null) return 0;
        long gap = ChronoUnit.DAYS.between(p.getLastDay(), today());
        if (gap <= 1) return p.getStreakCurrent();
        if (gap == 2 && p.getFreezes() > 0) return p.getStreakCurrent();
        return 0;
    }

    @Transactional
    public Result record(User u) {
        UserProgress p = rewards.of(u);
        LocalDate t = today();
        if (t.equals(p.getLastDay())) return new Result(false, p.getStreakCurrent(), false, List.of());
        int s; boolean froze = false;
        if (p.getLastDay() == null) {
            s = 1 + p.getHeadStart(); p.clearHeadStart();
        } else {
            long gap = ChronoUnit.DAYS.between(p.getLastDay(), t);
            if (gap == 1) s = p.getStreakCurrent() + 1;
            else if (gap == 2 && p.getFreezes() > 0) { p.useFreeze(); froze = true; s = p.getStreakCurrent() + 1; }
            else s = 1;
        }
        p.setStreak(s, t);
        List<String> got = new ArrayList<>();
        for (RewardService.Reward r : RewardService.CATALOG) {
            if (r.streakDays() > 0 && s >= r.streakDays() && rewards.grant(u.getId(), r.slug(), r.streakDays() + "-day streak")) got.add(r.title());
        }
        boolean qualify = p.getReferredBy() != null && p.getReferralQualifiedAt() == null;
        if (qualify) p.markQualified();
        progress.save(p);
        if (qualify) rewards.friendQualified(p.getReferredBy());
        return new Result(true, s, froze, got);
    }
}
