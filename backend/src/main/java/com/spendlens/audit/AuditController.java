package com.spendlens.audit;

import com.spendlens.auth.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/statements/{id}/audits")
public class AuditController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    /** Starts the audit agent in the background; poll {@code /latest} for steps and the report. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AuditRunResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.start(CurrentUser.id(jwt), id);
    }

    @GetMapping("/latest")
    public AuditRunResponse latest(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.latest(CurrentUser.id(jwt), id);
    }
}
