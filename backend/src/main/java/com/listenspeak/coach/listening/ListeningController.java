package com.listenspeak.coach.listening;

import com.listenspeak.coach.listening.api.CreateExerciseRequest;
import com.listenspeak.coach.listening.api.ExercisePublicView;
import com.listenspeak.coach.listening.api.SubmissionRequest;
import com.listenspeak.coach.listening.api.SubmissionResultView;
import com.listenspeak.coach.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/listening")
@Tag(name = "Listening", description = "Generate, practise, and submit listening exercises")
public class ListeningController {

    private final ListeningService service;
    private final CurrentUser currentUser;

    public ListeningController(ListeningService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/exercises")
    @Operation(
            summary = "Generate a new exercise",
            description = "Returns the scenario, audio link, and questions. The transcript and answer key "
                    + "are not part of this response type.")
    public ResponseEntity<ExercisePublicView> create(
            @Valid @RequestBody CreateExerciseRequest request,
            @Parameter(description = "Repeat-safe key; the same key returns the original exercise")
                    @RequestHeader(name = "Idempotency-Key", required = false)
                    String idempotencyKey) {

        ExercisePublicView view = service.create(currentUser.require().id(), request, idempotencyKey);
        return ResponseEntity.created(URI.create("/api/v1/listening/exercises/" + view.id()))
                .body(view);
    }

    @GetMapping("/exercises/{exerciseId}")
    @Operation(summary = "Fetch an exercise you own, without answers")
    public ExercisePublicView get(@PathVariable UUID exerciseId) {
        return service.get(currentUser.require().id(), exerciseId);
    }

    @PostMapping("/exercises/{exerciseId}/submissions")
    @Operation(
            summary = "Submit answers",
            description = "Scores the attempt and returns the answer key, rationale, evidence, "
                    + "full transcript, and one targeted tip.")
    public SubmissionResultView submit(
            @PathVariable UUID exerciseId, @Valid @RequestBody SubmissionRequest request) {
        return service.submit(currentUser.require().id(), exerciseId, request);
    }
}
