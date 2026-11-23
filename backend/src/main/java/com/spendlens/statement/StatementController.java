package com.spendlens.statement;

import com.spendlens.auth.CurrentUser;
import com.spendlens.common.BadRequestException;
import com.spendlens.insights.Insights;
import com.spendlens.statement.StatementDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
public class StatementController {

    private final StatementService service;

    public StatementController(StatementService service) {
        this.service = service;
    }

    @PostMapping(path = "/statements", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public StatementResponse upload(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("The file is empty");
        }
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            return service.upload(CurrentUser.id(jwt), file.getOriginalFilename(), content);
        } catch (IOException e) {
            throw new BadRequestException("Could not read the file");
        }
    }

    @GetMapping("/statements")
    public List<StatementResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUser.id(jwt));
    }

    @GetMapping("/statements/{id}")
    public StatementResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.get(CurrentUser.id(jwt), id);
    }

    @GetMapping("/statements/{id}/transactions")
    public List<TransactionResponse> transactions(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.transactions(CurrentUser.id(jwt), id);
    }

    @PatchMapping("/statements/{id}/transactions/{txId}")
    public TransactionResponse recategorize(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                            @PathVariable Long txId, @Valid @RequestBody RecategorizeRequest request) {
        return service.recategorize(CurrentUser.id(jwt), id, txId, request.category());
    }

    @GetMapping("/statements/{id}/insights")
    public Insights insights(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.insights(CurrentUser.id(jwt), id);
    }

    @PostMapping("/statements/{id}/ask")
    public AskResponse ask(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                           @Valid @RequestBody AskRequest request) {
        return service.ask(CurrentUser.id(jwt), id, request.question());
    }

    @DeleteMapping("/statements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.delete(CurrentUser.id(jwt), id);
    }

    @GetMapping("/ai/status")
    public AiStatus aiStatus() {
        return service.aiStatus();
    }

    @GetMapping("/categories")
    public List<CategoryInfo> categories() {
        return java.util.Arrays.stream(Category.values())
                .map(c -> new CategoryInfo(c, c.label(), c.color()))
                .toList();
    }

    public record CategoryInfo(Category value, String label, String color) {
    }
}
