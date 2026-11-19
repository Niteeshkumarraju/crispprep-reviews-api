package com.crispprep.reviews.controller;

import com.crispprep.reviews.entity.RewardFile;
import com.crispprep.reviews.entity.RewardGrant;
import com.crispprep.reviews.entity.User;
import com.crispprep.reviews.entity.UserProgress;
import com.crispprep.reviews.repository.QuizAttemptRepository;
import com.crispprep.reviews.repository.RewardFileRepository;
import com.crispprep.reviews.repository.UserProgressRepository;
import com.crispprep.reviews.repository.UserRepository;
import com.crispprep.reviews.service.ActivityService;
import com.crispprep.reviews.service.CurrentUser;
import com.crispprep.reviews.service.RewardService;
import com.crispprep.reviews.service.StreakService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Streak, rewards and referral for the signed-in user, the locked book downloads, and the public referral leaderboard. */
@RestController
public class ProgressController {

    private final CurrentUser currentUser;
    private final RewardService rewards;
    private final UserProgressRepository progress;
    private final RewardFileRepository files;
    private final UserRepository users;
    private final QuizAttemptRepository attempts;

    public ProgressController(CurrentUser currentUser, RewardService rewards, UserProgressRepository progress,
                              RewardFileRepository files, UserRepository users, QuizAttemptRepository attempts) {
        this.currentUser = currentUser;
        this.rewards = rewards;
        this.progress = progress;
        this.files = files;
        this.users = users;
        this.attempts = attempts;
    }

    @GetMapping("/api/me/progress")
    @Transactional
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        User u = currentUser.from(jwt);
        UserProgress p = rewards.of(u);
        int shown = StreakService.shown(p);
        LocalDate today = StreakService.today();
        Map<String, RewardGrant> mine = rewards.grantsOf(u.getId());
        Set<String> uploaded = rewards.uploaded();
        long friends = progress.countByReferredByAndReferralQualifiedAtIsNotNull(u.getId());
        long pending = progress.findByReferredBy(u.getId()).stream().filter(x -> x.getReferralQualifiedAt() == null).count();
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> next = null;
        for (RewardService.Reward r : RewardService.CATALOG) {
            boolean unlocked = p.isAllAccess() || mine.containsKey(r.slug());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("slug", r.slug()); m.put("title", r.title()); m.put("subtitle", r.subtitle()); m.put("how", r.how());
            m.put("unlocked", unlocked); m.put("ready", uploaded.contains(r.slug()));
            m.put("reason", unlocked ? (mine.containsKey(r.slug()) ? mine.get(r.slug()).getReason() : "All-access (5 friends)") : null);
            if (!unlocked && r.streakDays() > 0) m.put("daysLeft", Math.max(0, r.streakDays() - shown));
            list.add(m);
            if (next == null && !unlocked && r.streakDays() > 0) next = Map.of("title", r.title(), "streakDays", r.streakDays(), "daysLeft", Math.max(1, r.streakDays() - shown));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("streak", shown);
        out.put("best", p.getStreakBest());
        out.put("studiedToday", today.equals(p.getLastDay()));
        out.put("freezes", p.getFreezes());
        out.put("refCode", p.getRefCode());
        out.put("friends", friends);
        out.put("friendsPending", pending);
        out.put("credits", p.getCredits());
        out.put("allAccess", p.isAllAccess());
        out.put("quizDays", attempts.countByUserId(u.getId()));
        out.put("next", next);
        out.put("rewards", list);
        out.put("tiers", List.of(Map.of("friends", 1, "gift", "Puzzles & Seating Master + 1 streak freeze"),
                Map.of("friends", 3, "gift", "Unlock any 2 premium books you choose"), Map.of("friends", 5, "gift", "Every premium book, including new ones we add")));
        return out;
    }

    /** Spend a "pick any book" credit (earned at 3 friends). */
    @PostMapping("/api/me/rewards/{slug}/claim")
    @Transactional
    public Map<String, Object> claim(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) {
        User u = currentUser.from(jwt);
        if (!RewardService.SLUGS.contains(slug)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown book");
        UserProgress p = rewards.of(u);
        if (rewards.hasAccess(p, slug)) return Map.of("unlocked", true);
        if (p.getCredits() <= 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No book credits left. Invite more friends!");
        rewards.grant(u.getId(), slug, "Picked with a friend credit");
        p.useCredit();
        progress.save(p);
        return Map.of("unlocked", true, "credits", p.getCredits());
    }

    /** The locked PDF itself: only for users who unlocked it. */
    @GetMapping("/api/me/rewards/{slug}/file")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> file(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) {
        User u = currentUser.from(jwt);
        if (!RewardService.SLUGS.contains(slug)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown book");
        if (!rewards.hasAccess(rewards.of(u), slug)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You haven't unlocked this book yet.");
        RewardFile f = files.findById(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This book is being prepared. Check back soon."));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.getFileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(f.getData());
    }

    /** Public: top inviters this month (IST), shown as "First L.". */
    @GetMapping("/api/referrals/leaderboard")
    public List<Map<String, Object>> leaderboard() {
        Instant since = StreakService.today().withDayOfMonth(1).atStartOfDay(StreakService.IST).toInstant();
        List<UserProgressRepository.Top> top = progress.topReferrers(since, PageRequest.of(0, 10));
        Map<Long, User> who = new HashMap<>();
        users.findAllById(top.stream().map(UserProgressRepository.Top::getUserId).toList()).forEach(x -> who.put(x.getId(), x));
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserProgressRepository.Top t : top) {
            User x = who.get(t.getUserId());
            if (x != null && !x.isBlocked()) out.add(Map.of("name", ReviewController.publicName(x.getName()), "friends", t.getN()));
        }
        return out;
    }
}
