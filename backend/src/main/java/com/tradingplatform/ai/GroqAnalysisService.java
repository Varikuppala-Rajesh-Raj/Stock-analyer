package com.tradingplatform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradingplatform.signal.SignalResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GroqAnalysisService {

    private static final String SYSTEM =
            "Use only supplied quantitative facts. "
                    + "Never invent or alter prices, indicators, scores, entry, stop, or target. "
                    + "No guarantees. "
                    + "Return JSON with summary, why, supportingFactors, risks, "
                    + "invalidationConditions; each list contains concise strings.";

    private final String key;
    private final String model;
    private final ObjectMapper json;

    private final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

    public GroqAnalysisService(
            ObjectMapper json,
            @Value("${GROQ_API_KEY:}") String key,
            @Value("${GROQ_MODEL:llama-3.3-70b-versatile}") String model) {

        this.json = json;
        this.key = key;
        this.model = model;
    }

    public AiAnalysisResponse explain(SignalResult analysis) {

        if (key == null || key.isBlank()) {
            return AiAnalysisResponse.unavailable(analysis);
        }

        try {
            ObjectNode body = json.createObjectNode();

            body.put("model", model);

            ArrayNode messages = body.putArray("messages");

            ObjectNode systemMessage = messages.addObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", SYSTEM);

            ObjectNode userMessage = messages.addObject();
            userMessage.put("role", "user");
            userMessage.put(
                    "content",
                    json.writeValueAsString(analysis)
            );

            body.put("temperature", 0);

            ObjectNode responseFormat =
                    body.putObject("response_format");

            responseFormat.put("type", "json_object");

            HttpRequest request =
                    HttpRequest.newBuilder(
                                    URI.create(
                                            "https://api.groq.com/openai/v1/chat/completions"
                                    )
                            )
                            .timeout(Duration.ofSeconds(15))
                            .header(
                                    "Authorization",
                                    "Bearer " + key
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            json.writeValueAsString(body)
                                    )
                            )
                            .build();

            HttpResponse<String> response =
                    http.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (response.statusCode() / 100 != 2) {
                return AiAnalysisResponse.unavailable(analysis);
            }

            JsonNode content =
                    json.readTree(response.body())
                            .path("choices")
                            .path(0)
                            .path("message")
                            .path("content");

            if (!content.isTextual()) {
                return AiAnalysisResponse.unavailable(analysis);
            }

            JsonNode parsed =
                    json.readTree(content.asText());

            return new AiAnalysisResponse(
                    true,
                    "OK",
                    requiredText(parsed, "summary"),
                    strings(parsed, "why"),
                    strings(parsed, "supportingFactors"),
                    strings(parsed, "risks"),
                    strings(parsed, "invalidationConditions"),
                    analysis
            );

        } catch (Exception ignored) {
            return AiAnalysisResponse.unavailable(analysis);
        }
    }

    private static String requiredText(
            JsonNode node,
            String field) {

        if (!node.path(field).isTextual()) {
            throw new IllegalArgumentException(
                    "Invalid Groq JSON: missing text field " + field
            );
        }

        return node.path(field).asText();
    }

    private static List<String> strings(
            JsonNode node,
            String field) {

        if (!node.path(field).isArray()) {
            throw new IllegalArgumentException(
                    "Invalid Groq JSON: field is not an array " + field
            );
        }

        List<String> values = new ArrayList<>();

        for (JsonNode item : node.path(field)) {

            if (!item.isTextual()) {
                throw new IllegalArgumentException(
                        "Invalid Groq JSON: array contains non-string value in "
                                + field
                );
            }

            values.add(item.asText());
        }

        return List.copyOf(values);
    }
}