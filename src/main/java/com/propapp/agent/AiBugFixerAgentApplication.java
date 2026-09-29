package com.propapp.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiBugFixerAgentApplication {

    public static void main(String[] args) {
        // Enforce a quick check for critical integration credentials at boot runtime
        if (System.getenv("OPENAI_API_KEY") == null || System.getenv("GITHUB_TOKEN") == null) {
            System.err.println("⚠️ WARNING: Crucial infrastructure context missing.");
            System.err.println("Please verify OPENAI_API_KEY and GITHUB_TOKEN are set in your environment.");
        }
        
        SpringApplication.run(AiBugFixerAgentApplication.class, args);
        System.out.println("🤖 AI Bug Fixer DevOps Agent successfully listening for webhook streams...");
    }
}
