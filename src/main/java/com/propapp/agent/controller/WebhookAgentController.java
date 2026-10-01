package com.propapp.agent.controller;

import com.propapp.agent.util.AutonomousBugFixerAgent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookAgentController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ResponseEntity<String> handleCompilationError(
            @RequestBody String payload,
            @RequestHeader("X-GitHub-Event") String eventType) {
        try {
            if (!"issues".equalsIgnoreCase(eventType)) {
                return ResponseEntity.ok("Event ignored: Not an issue event.");
            }

            JsonNode rootNode = objectMapper.readTree(payload);
            String action = rootNode.path("action").asText();

            if ("opened".equalsIgnoreCase(action)) {
                String issueTitle = rootNode.path("issue").path("title").asText();
                String issueBody = rootNode.path("issue").path("body").asText();
                int issueNumber = rootNode.path("issue").path("number").asInt();

                String repoFullName = rootNode.path("repository").path("full_name").asText();
                String cloneUrl = rootNode.path("repository").path("clone_url").asText();

                String fullBugDescription = "ISSUE-" + issueNumber + ": " + issueTitle + "\n\n" + issueBody;

                System.out.println("📬 Webhook received for Issue #" + issueNumber + " on repo: " + repoFullName);

                // 1. Extract the target branch string from your structured webhook payload
                String targetBranch = rootNode.path("repository").path("default_branch").asText();
                if (targetBranch == null || targetBranch.trim().isEmpty()) {
                    targetBranch = "main"; // Safe fallback
                }
                
                // Final copies of values for safe multi-threaded memory access closure boundaries
                final String finalBugDesc = fullBugDescription;
                final String finalRepo = repoFullName;
                final String finalUrl = cloneUrl;
                final String finalBranch = targetBranch;

                // 2. Offload the heavy multi-minute execution sequence to a background daemon thread
                CompletableFuture.runAsync(() -> {
                    try {
                        System.out.println("🚀 Background thread spun up. Launching self-healing orchestration loop...");
                        AutonomousBugFixerAgent.orchestrateFullLifecycle(finalBugDesc, finalRepo, finalUrl, finalBranch);
                        System.out.println("🏁 Background thread successfully completed full lifecycle loop.");
                    } catch (Exception e) {
                        System.err.println("❌ Critical failure inside autonomous background orchestration worker thread: " + e.getMessage());
                        e.printStackTrace();
                    }
                });

                // 3. IMMEDIATELY return HTTP 202 Accepted to release the GitHub Actions network runner hook
                System.out.println("✅ Request acknowledged safely. Returning HTTP 202 status code to caller.");
                return ResponseEntity.status(HttpStatus.ACCEPTED).body("Agent triggered successfully. Processing auto-repair in the background.");
            }

            return ResponseEntity.ok("Event ignored: Issue action was " + action);

        } catch (Exception e) {
            System.err.println("❌ Error parsing webhook payload: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing payload.");
        }
    }
            
    @PostMapping("/github")
    public ResponseEntity<String> handleGitHubWebhook(
            @RequestBody String payload,
            @RequestHeader("X-GitHub-Event") String eventType) {
        return handleCompilationError(payload, eventType);
    }
}
