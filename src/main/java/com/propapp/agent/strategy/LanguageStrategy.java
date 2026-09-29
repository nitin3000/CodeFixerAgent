package com.propapp.agent.strategy;

public interface LanguageStrategy {
    String getTestCommand();
    String getEcosystemName();
}

class SpringBootStrategy implements LanguageStrategy {
    @Override public String getTestCommand() { return "mvn clean test"; }
    @Override public String getEcosystemName() { return "Java Spring Boot"; }
}

class GoStrategy implements LanguageStrategy {
    @Override public String getTestCommand() { return "go test ./..."; }
    @Override public String getEcosystemName() { return "Go Lang"; }
}

class PythonStrategy implements LanguageStrategy {
    @Override public String getTestCommand() { return "pytest"; }
    @Override public String getEcosystemName() { return "Python Ecosystem"; }
}

class TypeScriptUiStrategy implements LanguageStrategy {
    private final String command;
    public TypeScriptUiStrategy(String command) { this.command = command; }
    @Override public String getTestCommand() { return command; }
    @Override public String getEcosystemName() { return "Frontend TS/JS Node runtime"; }
}
