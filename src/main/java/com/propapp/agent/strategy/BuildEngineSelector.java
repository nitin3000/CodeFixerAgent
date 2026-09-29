package com.propapp.agent.strategy;

import java.nio.file.Files;
import java.nio.file.Path;

public class BuildEngineSelector {
    public static LanguageStrategy detectLanguage(Path workspaceDir) {
        if (Files.exists(workspaceDir.resolve("pom.xml"))) {
            return new SpringBootStrategy();
        } else if (Files.exists(workspaceDir.resolve("go.mod"))) {
            return new GoStrategy();
        } else if (Files.exists(workspaceDir.resolve("requirements.txt"))) {
            return new PythonStrategy();
        } else if (Files.exists(workspaceDir.resolve("package.json"))) {
            if (Files.exists(workspaceDir.resolve("angular.json"))) {
                return new TypeScriptUiStrategy("npm run test"); // Or "ng test --watch=false"
            }
            return new TypeScriptUiStrategy("npm run test"); // Default React test runner scripts
        }
        throw new IllegalArgumentException("Unsupported language ecosystem detected at repository workspace root directory.");
    }
}
