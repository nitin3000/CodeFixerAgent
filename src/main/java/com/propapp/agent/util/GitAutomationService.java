package com.propapp.agent.util;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class GitAutomationService {

    // Reads the secure environment token we injected into OpenShift
    private static final String GITHUB_TOKEN = System.getenv("GITHUB_TOKEN");

    /**
     * Clones the repository, creates a temporary workspace directory, and returns the path.
     */
    public static Path cloneRepository(String repoUrl, String branch) throws IOException, GitAPIException {
        Path tempWorkspace = Files.createTempDirectory("agent-workspace-");
        System.out.println("📂 Created isolated staging directory at: " + tempWorkspace);

        System.out.println("🔄 Cloning branch '" + branch + "' from: " + repoUrl);
        Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(tempWorkspace.toFile())
                .setBranch(branch)
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(GITHUB_TOKEN, ""))
                .call()
                .close();

        return tempWorkspace;
    }

    /**
     * Commits all changes inside the workspace workspace and pushes them back to GitHub securely.
     */
    public static void commitAndPushFixes(Path workspaceRoot, String commitMessage) throws IOException, GitAPIException {
        System.out.println("🚀 Committing and pushing automated patches back to remote repository...");
        
        try (Git git = Git.open(workspaceRoot.toFile())) {
            // Stage all modified files
            git.add().addFilepattern(".").call();

            // Check if there are any actual changes to commit
            boolean hasChanges = !git.status().call().isClean();
            if (!hasChanges) {
                System.out.println("✅ Workspace is clean. No code modifications were needed.");
                return;
            }

            // Create the commit
            git.commit()
                    .setMessage(commitMessage)
                    .setAuthor("AI Bug Fixer Agent", "agent@propapp.com")
                    .setCommitter("AI Bug Fixer Agent", "agent@propapp.com")
                    .call();

            // Push the commit to the cloud tracking branch using the fine-grained token
            git.push()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(GITHUB_TOKEN, ""))
                    .call();
            
            System.out.println("🎉 Patches successfully merged and pushed to GitHub cloud storage!");
        }
    }

    /**
     * Clean up helper to erase temporary working directory data once execution completes.
     */
    public static void cleanupWorkspace(Path workspaceRoot) throws IOException {
        if (Files.exists(workspaceRoot)) {
            Files.walk(workspaceRoot)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            System.out.println("🧹 Cleaned up temporary storage directories safely.");
        }
    }
}
