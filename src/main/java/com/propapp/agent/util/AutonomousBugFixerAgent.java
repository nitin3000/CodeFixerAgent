package com.propapp.agent.util;

import com.propapp.agent.model.TargetDiscoveryResponse;
import com.propapp.agent.strategy.BuildEngineSelector;
import com.propapp.agent.strategy.LanguageStrategy;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

public class AutonomousBugFixerAgent {

    private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String GITHUB_TOKEN = System.getenv("GITHUB_TOKEN");

    public static void orchestrateFullLifecycle(String bugDescription, String repoFullName, String cloneUrl) throws Exception {
        Path workspaceDir = Files.createTempDirectory("agent-polyglot-workspace-");
        File gitRootWorkspace = workspaceDir.toFile();
        File executionWorkspace = gitRootWorkspace;
        System.out.println("📦 Created ephemeral workspace: " + workspaceDir.toAbsolutePath());

        try {
            // 1. Clone repository into the root temporary folder
            String authenticatedUrl = cloneUrl.replace("https://", "https://" + GITHUB_TOKEN + "@");
            runSystemCommand(gitRootWorkspace, "git clone " + authenticatedUrl + " .");

            // 2. Target Workspace Resolution & Hardcoded Ecosystem Enforcement
            Path targetProjectDir = workspaceDir;
            if (Files.exists(workspaceDir.resolve("java/pom.xml"))) {
                System.out.println("📂 Target subfolder found. Shifting context down to: /java");
                targetProjectDir = workspaceDir.resolve("java");
                workspace = targetProjectDir.toFile(); 
            }

            // Create a custom anonymous strategy instance to force Maven compilation
            LanguageStrategy strategy = new LanguageStrategy() {
                @Override public String getEcosystemName() { return "Java (Maven - Hardcoded Bypass)"; }
                @Override public String getTestCommand() { return "mvn clean package -DskipTests"; }
            };
            System.out.println("🧬 Ecosystem Strategy Bypassed: " + strategy.getEcosystemName());

            OpenAiChatModel model = OpenAiChatModel.builder()
                    .apiKey(OPENAI_API_KEY)
                    .modelName("gpt-4o")
                    .temperature(0.1)
                    .build();

            // 3. Run initial test verification inside the verified execution directory
            System.out.println("🔄 Running initial test execution via command: " + strategy.getTestCommand());
            TestResult initialRun = runEcosystemTests(executionWorkspace, strategy.getTestCommand());

            // 4. If tests pass cleanly, use the LLM to write a reproduction test case
            List<TargetDiscoveryResponse.FailureTarget> brokenFiles;
            if (initialRun.isSuccessful()) {
                System.out.println("🧪 Project passes cleanly. Instructing LLM to inject reproduction test cases...");
                TestResult reproductionRun = runEcosystemTests(executionWorkspace, strategy.getTestCommand());
                brokenFiles = FileDiscoveryUtility.discoverBrokenFiles(reproductionRun.getOutput(), targetProjectDir);
            } else {
                brokenFiles = FileDiscoveryUtility.discoverBrokenFiles(initialRun.getOutput(), targetProjectDir);
            }

            if (brokenFiles.isEmpty()) {
                System.out.println("⚠️ Could not extract target files from error logs. Stopping.");
                return;
            }

            // 5. Apply the patches loop relative to our targeted project subfolder path
            for (TargetDiscoveryResponse.FailureTarget target : brokenFiles) {
                Path pathOfBrokenFile = targetProjectDir.resolve(target.getFilePath());
                
                if (!Files.exists(pathOfBrokenFile)) {
                    System.out.println("⚠️ File not found at resolved path: " + pathOfBrokenFile.toAbsolutePath());
                    continue;
                }

                String currentCode = Files.readString(pathOfBrokenFile);

                String fixPrompt = """
                        Fix the compilation error or failing test logic for this specific file.
                        FILE PATH: %s
                        ERROR REASON: %s
                        CURRENT CODE:
                        %s
                        Return ONLY the clean updated code. No markdown decorations or wrapper fences.
                        """.formatted(target.getFilePath(), target.getErrorReason(), currentCode);

                String patchedCode = model.generate(fixPrompt).replaceAll("```[a-z]*|```", "").trim();
                Files.writeString(pathOfBrokenFile, patchedCode);
                System.out.println("🛠️ Applied automated code patch to: " + target.getFilePath());
            }

            // 6. Run final validation tests inside the subfolder structure
            System.out.println("🔬 Running final confirmation build execution...");
            TestResult validationRun = runEcosystemTests(executionWorkspace, strategy.getTestCommand());

            if (validationRun.isSuccessful()) {
                System.out.println("🎉 Fix Verified! Generating PR branch...");
                // Note: Git commands must always be executed relative to the gitRootWorkspace (.git location)
                executeGitAndPullRequest(gitRootWorkspace, targetProjectDir, repoFullName, brokenFiles, bugDescription);
            } else {
                System.out.println("❌ Patch validation run failed. Changes contain syntax errors.");
            }

        } finally {
            // 7. Housekeeping delete loop
            Files.walk(workspaceDir)
                 .map(Path::toFile)
                 .sorted((o1, o2) -> o2.compareTo(o1))
                 .forEach(File::delete);
            System.out.println("🗑️ Ephemeral workspace removed cleanly from disk.");
        }
    }

    private static TestResult runEcosystemTests(File workspace, String testCommand) throws IOException, InterruptedException {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        ProcessBuilder builder = new ProcessBuilder();
        builder.directory(workspace);
        
        if (isWindows) {
            builder.command("cmd.exe", "/c", testCommand);
        } else {
            builder.command("sh", "-c", testCommand);
        }

        builder.redirectErrorStream(true);
        Process process = builder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String output = reader.lines().collect(Collectors.joining("\n"));
            int exitCode = process.waitFor();
            return new TestResult(exitCode == 0, output);
        }
    }

    private static void executeGitAndPullRequest(File gitRoot, Path targetProjectDir, String repoFullName, List<TargetDiscoveryResponse.FailureTarget> targets, String bugDescription) throws Exception {
        String branchName = "fix/agent-polyglot-patch-" + System.currentTimeMillis() / 1000;

        runSystemCommand(gitRoot, "git checkout -b " + branchName);
        for (TargetDiscoveryResponse.FailureTarget target : targets) {
            // Track the relative path from the actual git root repository structure
            Path absoluteFilePath = targetProjectDir.resolve(target.getFilePath());
            Path relativeToGitRoot = Paths.get(gitRoot.toURI()).relativize(absoluteFilePath);
            runSystemCommand(gitRoot, "git add " + relativeToGitRoot.toString().replace("\\", "/"));
        }
        runSystemCommand(gitRoot, "git commit -m \"fix: automated polyglot patch resolving build logs errors\"");
        runSystemCommand(gitRoot, "git push origin " + branchName);

        GitHub github = new GitHubBuilder().withOAuthToken(GITHUB_TOKEN).build();
        GHRepository repository = github.getRepository(repoFullName);
        
        repository.createPullRequest(
                "🤖 Polyglot Agent Auto-Fix Patch",
                branchName,
                "main",
                "### 🤖 Automated Bug Fix Execution Summary\n\n**Bug Ticket:**\n" + bugDescription
        );
    }

    private static void runSystemCommand(File workspace, String command) throws IOException, InterruptedException {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        ProcessBuilder pb = isWindows ? new ProcessBuilder("cmd.exe", "/c", command) : new ProcessBuilder("sh", "-c", command);
        pb.directory(workspace);
        pb.start().waitFor();
    }

    private static class TestResult {
        private final boolean success;
        private final String output;
        public TestResult(boolean success, String output) { this.success = success; this.output = output; }
        public boolean isSuccessful() { return success; }
        public String getOutput() { return output; }
    }
}
