package com.crispprep.reviews.controller;

import com.crispprep.reviews.entity.QuizAttempt;
import com.crispprep.reviews.entity.User;
import com.crispprep.reviews.repository.QuizAttemptRepository;
import com.crispprep.reviews.service.CurrentUser;
import com.crispprep.reviews.service.StreakService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;

/** The Daily Quiz: the same 5 questions for everyone on an IST day, picked in order from the question bank. */
@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    public static final int PER_DAY = 5;
    public record Answers(@NotNull @Size(min = PER_DAY, max = PER_DAY) List<Integer> answers) { }
    record Q(String q, List<String> o, int a, String x, String t) { }

    private final List<Q> bank = new ArrayList<>();
    private final QuizAttemptRepository attempts;
    private final CurrentUser currentUser;
    private final StreakService streaks;

    public QuizController(QuizAttemptRepository attempts, CurrentUser currentUser, StreakService streaks) throws IOException {
        ObjectMapper json = JsonMapper.builder().build();
        this.attempts = attempts;
        this.currentUser = currentUser;
        this.streaks = streaks;
        try (InputStream in = new ClassPathResource("quiz/bank.json").getInputStream()) {
            for (JsonNode n : json.readTree(in).get("questions")) {
                List<String> o = new ArrayList<>();
                n.get("o").forEach(v -> o.add(v.asString()));
                bank.add(new Q(n.get("q").asString(), o, n.get("a").asInt(), n.get("x").asString(), n.get("t").asString()));
            }
        }
    }

    private List<Q> forDay(LocalDate day) {
        long start = Math.floorMod(day.toEpochDay() * PER_DAY, bank.size());
        List<Q> out = new ArrayList<>();
        for (int i = 0; i < PER_DAY; i++) out.add(bank.get((int) ((start + i) % bank.size())));
        return out;
    }

    private Map<String, Object> result(List<Q> qs, QuizAttempt a, StreakService.Result r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("score", a.getScore());
        m.put("total", a.getTotal());
        m.put("yours", Arrays.stream(a.getAnswers().split(",")).map(Integer::valueOf).toList());
        m.put("correct", qs.stream().map(Q::a).toList());
        m.put("explanations", qs.stream().map(Q::x).toList());
        if (r != null) { m.put("streak", r.streak()); m.put("newDay", r.newDay()); m.put("freezeUsed", r.freezeUsed()); m.put("unlocked", r.unlocked()); }
        return m;
    }

    /** Public: today's questions (no answers). With a token, also your result if you already played today. */
    @GetMapping("/today")
    public Map<String, Object> today(@AuthenticationPrincipal Jwt jwt) {
        LocalDate d = StreakService.today();
        List<Q> qs = forDay(d);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", d.toString());
        out.put("questions", qs.stream().map(q -> Map.of("q", q.q(), "o", q.o(), "topic", q.t())).toList());
        out.put("players", attempts.countByDay(d));
        if (jwt != null) attempts.findByUserIdAndDay(Long.valueOf(jwt.getSubject()), d).ifPresent(a -> out.put("result", result(qs, a, null)));
        return out;
    }

    /** Public: extra practice after the daily quiz. Random questions (not today's), WITH answers: checked in the browser, no streak. */
    @GetMapping("/practice")
    public List<Map<String, Object>> practice(@RequestParam(defaultValue = "5") int n) {
        Set<Q> today = new HashSet<>(forDay(StreakService.today()));
        List<Q> pool = new ArrayList<>(bank); pool.removeAll(today);
        Collections.shuffle(pool);
        return pool.subList(0, Math.min(Math.max(n, 1), 10)).stream()
                .map(q -> Map.<String, Object>of("q", q.q(), "o", q.o(), "a", q.a(), "x", q.x(), "topic", q.t())).toList();
    }

    /** Submit today's answers (signed in). One attempt per day; it counts as a study day for the streak. */
    @PostMapping("/today")
    public Map<String, Object> submit(@Valid @RequestBody Answers body, @AuthenticationPrincipal Jwt jwt) {
        User u = currentUser.from(jwt);
        LocalDate d = StreakService.today();
        List<Q> qs = forDay(d);
        Optional<QuizAttempt> old = attempts.findByUserIdAndDay(u.getId(), d);
        if (old.isPresent()) return result(qs, old.get(), null);
        int score = 0;
        for (int i = 0; i < PER_DAY; i++) {
            Integer a = body.answers().get(i);
            if (a == null || a < -1 || a >= qs.get(i).o().size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid answer");
            if (a == qs.get(i).a()) score++;
        }
        QuizAttempt saved = attempts.save(new QuizAttempt(u.getId(), d, score, PER_DAY,
                String.join(",", body.answers().stream().map(String::valueOf).toList())));
        return result(qs, saved, streaks.record(u));
    }
}
