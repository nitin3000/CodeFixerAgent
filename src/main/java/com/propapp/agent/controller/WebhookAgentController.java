package com.propapp.agent.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.propapp.agent.util.AutonomousBugFixerAgent;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookAgentController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/github")
    public ResponseEntity<String> handleGitHubWebhook(
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

// 1. Extract the target branch string from your new structured webhook payload
String targetBranch = rootNode.path("repository").path("default_branch").asText();
if (targetBranch == null || targetBranch.isEmpty()) {
    targetBranch = "main"; // Safe fallback
}
                
AutonomousBugFixerAgent.orchestrateFullLifecycle(
    fullBugDescription, 
    repoFullName, 
    cloneUrl, 
    targetBranch // 👈 ADD THIS PARAMETER
);
                return ResponseEntity.status(HttpStatus.ACCEPTED).body("Agent triggered successfully.");
            }

            return ResponseEntity.ok("Event ignored: Issue action was " + action);

        } catch (Exception e) {
            System.err.println("❌ Error parsing webhook payload: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing payload.");
        }
    }
}
