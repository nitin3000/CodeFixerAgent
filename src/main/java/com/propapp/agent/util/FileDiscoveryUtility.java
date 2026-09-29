package com.propapp.agent.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.propapp.agent.model.TargetDiscoveryResponse;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FileDiscoveryUtility {

    private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static List<TargetDiscoveryResponse.FailureTarget> discoverBrokenFiles(String rawBuildLogs, Path workspaceRoot) {
        System.out.println("🧐 Analyzing build logs to locate broken files...");

        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(OPENAI_API_KEY)
                .modelName("gpt-4o")
                .temperature(0.0)
                .build();

        String prompt = """
                Analyze the following raw build/test execution log output.
                Identify all application source code or test files that directly caused the compilation or test suite failure.
                
                RAW BUILD LOGS:
                %s
                
                CRITICAL INSTRUCTIONS:
                1. Extract the file paths relative to the project workspace root directory.
                2. Do not include external system libraries, dependency files, or compiled binaries.
                3. Map your extraction directly to the requested JSON schema structure.
                """.formatted(rawBuildLogs);

        JsonSchema jsonSchema = JsonSchema.builder()
                .rootElement(JsonObjectSchema.builder()
                        .properties(Map.of(
                                "failures", JsonArraySchema.builder()
                                        .items(JsonObjectSchema.builder()
                                                .properties(Map.of(
                                                        "filePath", JsonStringSchema.builder().description("Relative path to the broken source file").build(),
                                                        "errorReason", JsonStringSchema.builder().description("Short description of why the file failed").build()
                                                ))
                                                .required(List.of("filePath", "errorReason"))
                                                .build())
                                        .build()
                        ))
                        .required(List.of("failures"))
                        .build())
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(dev.langchain4j.data.message.UserMessage.from(prompt))
                .responseFormat(ResponseFormat.JSON)
                .jsonSchema(jsonSchema)
                .build();

        ChatResponse response = model.chat(request);
        
        try {
            TargetDiscoveryResponse mappedResponse = objectMapper.readValue(response.aiMessage().text(), TargetDiscoveryResponse.class);
            List<TargetDiscoveryResponse.FailureTarget> verifiedTargets = new ArrayList<>();
            
            for (TargetDiscoveryResponse.FailureTarget target : mappedResponse.getFailures()) {
                Path resolvedPath = workspaceRoot.resolve(target.getFilePath());
                if (Files.exists(resolvedPath)) {
                    verifiedTargets.add(target);
                    System.out.println("🎯 Discovered verified target file: " + target.getFilePath());
                }
            }
            return verifiedTargets;
        } catch (Exception e) {
            System.err.println("❌ Failed to parse file discovery response schema: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}
