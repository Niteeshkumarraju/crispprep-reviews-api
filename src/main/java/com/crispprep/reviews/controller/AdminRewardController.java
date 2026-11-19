package com.crispprep.reviews.controller;

import com.crispprep.reviews.entity.AdminAction;
import com.crispprep.reviews.entity.RewardFile;
import com.crispprep.reviews.entity.User;
import com.crispprep.reviews.repository.AdminActionRepository;
import com.crispprep.reviews.repository.QuizAttemptRepository;
import com.crispprep.reviews.repository.RewardFileRepository;
import com.crispprep.reviews.repository.RewardGrantRepository;
import com.crispprep.reviews.service.AdminGuard;
import com.crispprep.reviews.service.RewardService;
import com.crispprep.reviews.service.StreakService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;

/** Admin: upload the reward PDFs and see how many students unlocked each one. */
@RestController
@RequestMapping("/api/admin/rewards")
public class AdminRewardController {

    private final AdminGuard guard;
    private final RewardFileRepository files;
    private final RewardGrantRepository grants;
    private final AdminActionRepository actions;
    private final QuizAttemptRepository attempts;

    public AdminRewardController(AdminGuard guard, RewardFileRepository files, RewardGrantRepository grants,
                                 AdminActionRepository actions, QuizAttemptRepository attempts) {
        this.guard = guard;
        this.files = files;
        this.grants = grants;
        this.actions = actions;
        this.attempts = attempts;
    }

    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal Jwt jwt) {
        guard.require(jwt);
        Map<String, RewardFileRepository.Info> info = new HashMap<>();
        files.listInfo().forEach(i -> info.put(i.getSlug(), i));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RewardService.Reward r : RewardService.CATALOG) {
            RewardFileRepository.Info f = info.get(r.slug());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("slug", r.slug()); m.put("title", r.title()); m.put("how", r.how());
            m.put("uploaded", f != null);
            m.put("fileName", f == null ? null : f.getFileName()); m.put("size", f == null ? null : f.getSize());
            m.put("updatedAt", f == null ? null : f.getUpdatedAt().toString());
            m.put("unlocks", grants.countBySlug(r.slug()));
            rows.add(m);
        }
        return Map.of("rewards", rows, "quizPlayersToday", attempts.countByDay(StreakService.today()));
    }

    @PostMapping("/{slug}/file")
    @Transactional
    public Map<String, Object> upload(@PathVariable String slug, @RequestParam("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) throws IOException {
        User admin = guard.require(jwt);
        if (!RewardService.SLUGS.contains(slug)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown book");
        byte[] data = file.getBytes();
        if (data.length < 5 || data[0] != '%' || data[1] != 'P' || data[2] != 'D' || data[3] != 'F') {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That file is not a PDF.");
        }
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse(slug + ".pdf").replaceAll("[^A-Za-z0-9._-]", "-");
        if (name.length() > 150) name = name.substring(name.length() - 150);
        RewardFile f = files.findById(slug).orElseGet(() -> new RewardFile(slug));
        f.replace(name, data);
        files.save(f);
        actions.save(new AdminAction(admin.getId(), null, "uploaded reward book " + slug + " (" + data.length / 1024 + " KB)"));
        return Map.of("slug", slug, "fileName", name, "size", data.length);
    }
}
