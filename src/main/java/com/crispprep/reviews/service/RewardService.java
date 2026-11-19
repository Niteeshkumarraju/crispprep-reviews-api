package com.crispprep.reviews.service;

import com.crispprep.reviews.entity.RewardGrant;
import com.crispprep.reviews.entity.User;
import com.crispprep.reviews.entity.UserProgress;
import com.crispprep.reviews.repository.RewardFileRepository;
import com.crispprep.reviews.repository.RewardGrantRepository;
import com.crispprep.reviews.repository.UserProgressRepository;
import com.crispprep.reviews.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

/** The premium reward books, how each one unlocks, and who has unlocked what. */
@Service
public class RewardService {

    /** streakDays > 0: unlocks at that streak. referralOnly: unlocks when the first invited friend studies. */
    public record Reward(String slug, String title, String subtitle, int streakDays, boolean referralOnly) {
        public String how() { return referralOnly ? "Invite 1 friend" : streakDays + "-day streak"; }
    }

    public static final List<Reward> CATALOG = List.of(
            new Reward("speed-pack", "Bank Prelims Speed Pack", "Series, Simplification, Quadratic, Inequality · 31 pages", 3, false),
            new Reward("arithmetic-1", "Arithmetic Master I", "Percentage, Profit & Loss, Discount, SI & CI · 31 pages", 7, false),
            new Reward("arithmetic-2", "Arithmetic Master II", "Time & Work, Pipes, Speed-Distance, Trains, Boats · 30 pages", 14, false),
            new Reward("geometry", "Geometry & Mensuration Master", "Triangles, circles, polygons, 2D and 3D · 30 pages", 21, false),
            new Reward("data-interpretation", "Data Interpretation Master", "20 DI sets with charts · 30 pages", 30, false),
            new Reward("puzzles", "Puzzles & Seating Master", "16 puzzles with solution diagrams · 38 pages", 0, true));
    public static final Set<String> SLUGS = CATALOG.stream().map(Reward::slug).collect(Collectors.toUnmodifiableSet());

    public static final int FRIEND_HEAD_START = 2, FRIEND_GIFT_FREEZES = 1, MAX_FREEZES = 5;
    public static final int CREDITS_AT_3 = 2, ALL_ACCESS_AT = 5;

    private final UserProgressRepository progress;
    private final RewardGrantRepository grants;
    private final RewardFileRepository files;
    private final UserRepository users;
    private final SecureRandom rnd = new SecureRandom();

    public RewardService(UserProgressRepository progress, RewardGrantRepository grants, RewardFileRepository files, UserRepository users) {
        this.progress = progress;
        this.grants = grants;
        this.files = files;
        this.users = users;
    }

    public static Optional<Reward> find(String slug) { return CATALOG.stream().filter(r -> r.slug().equals(slug)).findFirst(); }

    /** Get or create the user's progress row (with a personal, readable invite code like ASHA7K2). */
    public UserProgress of(User u) {
        return progress.findById(u.getId()).orElseGet(() -> progress.save(new UserProgress(u.getId(), newCode(u.getName()))));
    }

    private String newCode(String name) {
        String base = name == null ? "" : name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
        base = base.isEmpty() ? "CP" : base.substring(0, Math.min(5, base.length()));
        String abc = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
        for (int i = 0; i < 50; i++) {
            StringBuilder sb = new StringBuilder(base);
            for (int k = 0; k < 3; k++) sb.append(abc.charAt(rnd.nextInt(abc.length())));
            if (progress.findByRefCode(sb.toString()).isEmpty()) return sb.toString();
        }
        throw new IllegalStateException("could not make an invite code");
    }

    /** Returns true if this is a new unlock. */
    public boolean grant(Long userId, String slug, String reason) {
        if (grants.existsByUserIdAndSlug(userId, slug)) return false;
        grants.save(new RewardGrant(userId, slug, reason));
        return true;
    }

    public boolean hasAccess(UserProgress p, String slug) {
        return p.isAllAccess() || grants.existsByUserIdAndSlug(p.getUserId(), slug);
    }

    public Map<String, RewardGrant> grantsOf(Long userId) {
        return grants.findByUserId(userId).stream().collect(Collectors.toMap(RewardGrant::getSlug, g -> g, (a, b) -> a));
    }

    public Set<String> uploaded() {
        return files.listInfo().stream().map(RewardFileRepository.Info::getSlug).collect(Collectors.toSet());
    }

    /** Called when an invited friend studies for the first time: the inviter earns their rewards. Returns new unlock titles. */
    public List<String> friendQualified(Long inviterId) {
        UserProgress p = progress.findById(inviterId).orElse(null);
        if (p == null) return List.of();
        List<String> got = new ArrayList<>();
        p.addFreezes(1, MAX_FREEZES);
        long n = progress.countByReferredByAndReferralQualifiedAtIsNotNull(inviterId);
        if (n >= 1 && grant(inviterId, "puzzles", "Invited 1 friend")) got.add("Puzzles & Seating Master");
        if (n == 3) p.addCredits(CREDITS_AT_3);
        if (n >= ALL_ACCESS_AT && !p.isAllAccess()) { p.grantAllAccess(); got.add("All premium books"); }
        progress.save(p);
        return got;
    }

    public Optional<User> userByCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return progress.findByRefCode(code.trim().toUpperCase(Locale.ROOT)).flatMap(p -> users.findById(p.getUserId()));
    }
}
