package com.interviewdeck.mcp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewdeck.mcp.model.CategoryInfo;
import com.interviewdeck.mcp.model.Question;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class QuestionService {

    private static final Logger log = LoggerFactory.getLogger(QuestionService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final List<Question> allQuestions = new ArrayList<>();
    private final Map<String, List<Question>> byCategory = new HashMap<>();
    private final List<CategoryInfo> categories = new ArrayList<>();

    @PostConstruct
    public void loadQuestions() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource manifestResource = resolver.getResource("classpath:questions/manifest.json");

        JsonNode manifest = objectMapper.readTree(manifestResource.getInputStream());
        JsonNode cats = manifest.get("categories");

        for (JsonNode cat : cats) {
            String id = cat.get("id").asText();
            String file = cat.get("file").asText();
            String label = cat.get("label").asText();
            String group = cat.get("group").asText();
            int count = cat.get("count").asInt();

            categories.add(new CategoryInfo(id, label, group, count));

            Resource questionFile = resolver.getResource("classpath:questions/" + file);
            try (InputStream is = questionFile.getInputStream()) {
                List<Question> questions = objectMapper.readValue(is, new TypeReference<>() {});
                allQuestions.addAll(questions);
                byCategory.put(id, questions);
            }
        }

        log.info("Loaded {} questions across {} categories", allQuestions.size(), categories.size());
    }

    public List<CategoryInfo> getCategories() {
        return Collections.unmodifiableList(categories);
    }

    public List<Question> searchQuestions(String query, String category, String difficulty, int limit) {
        String q = query != null ? query.toLowerCase() : null;

        return allQuestions.stream()
                .filter(question -> category == null || question.category().equalsIgnoreCase(category))
                .filter(question -> difficulty == null || difficulty.equalsIgnoreCase(question.difficulty()))
                .filter(question -> q == null ||
                        question.question().toLowerCase().contains(q) ||
                        question.answer().toLowerCase().contains(q) ||
                        (question.tags() != null && question.tags().stream().anyMatch(t -> t.toLowerCase().contains(q))))
                .limit(limit)
                .toList();
    }

    public Optional<Question> getQuestion(String id) {
        return allQuestions.stream()
                .filter(q -> q.id().equals(id))
                .findFirst();
    }

    public List<Question> getRandomQuestions(String category, String difficulty, int count, Set<String> excludeIds) {
        List<Question> pool = allQuestions.stream()
                .filter(q -> category == null || q.category().equalsIgnoreCase(category))
                .filter(q -> difficulty == null || difficulty.equalsIgnoreCase(q.difficulty()))
                .filter(q -> excludeIds == null || !excludeIds.contains(q.id()))
                .collect(Collectors.toCollection(ArrayList::new));

        Collections.shuffle(pool, ThreadLocalRandom.current());
        return pool.stream().limit(count).toList();
    }

    public List<Question> getQuestionsByIds(List<String> ids) {
        Set<String> idSet = new HashSet<>(ids);
        return allQuestions.stream()
                .filter(q -> idSet.contains(q.id()))
                .toList();
    }
}
