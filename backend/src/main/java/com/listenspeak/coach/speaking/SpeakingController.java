package com.listenspeak.coach.speaking;

import com.listenspeak.coach.platform.security.CurrentUser;
import com.listenspeak.coach.speaking.api.SpeakingViews.EvaluationView;
import com.listenspeak.coach.speaking.api.SpeakingViews.PromptView;
import com.listenspeak.coach.speaking.api.SpeakingViews.TaskView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/speaking")
@Tag(name = "Speaking", description = "Speaking task prompts and AI evaluation")
public class SpeakingController {

    private final SpeakingService service;
    private final CurrentUser currentUser;

    public SpeakingController(SpeakingService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/tasks")
    @Operation(summary = "List the eight speaking tasks with their timings")
    public List<TaskView> tasks() {
        return service.tasks().stream().map(TaskView::of).toList();
    }

    @PostMapping("/tasks/{taskNumber}/prompts")
    @Operation(summary = "Generate an original prompt for one task")
    public ResponseEntity<PromptView> createPrompt(
            @PathVariable @Min(1) @Max(8) int taskNumber) {

        PromptView prompt = service.createPrompt(currentUser.require().id(), taskNumber);
        return ResponseEntity.created(URI.create("/api/v1/speaking/prompts/" + prompt.id()))
                .body(prompt);
    }

    @PostMapping(value = "/evaluations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Evaluate a recorded answer",
            description = "Accepts the completed recording, transcribes it, measures delivery, and returns "
                    + "an unofficial estimate with evidence. The recording is deleted after evaluation.")
    public EvaluationView evaluate(
            @RequestParam("promptId") UUID promptId, @RequestPart("recording") MultipartFile recording) {

        return service.evaluate(currentUser.require().id(), promptId, recording);
    }

    @GetMapping("/evaluations/{evaluationId}")
    @Operation(summary = "Fetch an evaluation you own")
    public EvaluationView getEvaluation(@PathVariable UUID evaluationId) {
        return service.getEvaluation(currentUser.require().id(), evaluationId);
    }
}
