package com.edutrack.notification;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service = service; }

    @GetMapping("/notifications")
    public Page<NotificationService.Notice> list(JwtAuthenticationToken principal,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return service.list(UUID.fromString(principal.getName()), page, size);
    }

    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unreadCount(JwtAuthenticationToken principal) {
        return Map.of("count", service.unreadCount(UUID.fromString(principal.getName())));
    }

    @PostMapping("/notifications/{id}/read")
    public NotificationService.Notice markRead(@PathVariable UUID id, JwtAuthenticationToken principal) {
        return service.markRead(UUID.fromString(principal.getName()), id);
    }

    @PostMapping("/notifications/read-all")
    public Map<String, Integer> markAllRead(JwtAuthenticationToken principal) {
        return Map.of("updated", service.markAllRead(UUID.fromString(principal.getName())));
    }

    @PostMapping("/admin/notifications")
    @PreAuthorize("hasRole('ADMIN_TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationService.Notice create(@Valid @RequestBody NotificationService.CreateRequest request) {
        return service.create(request);
    }
}
