package com.interviewdeck.mcp.config;

import com.interviewdeck.mcp.model.Question;
import com.interviewdeck.mcp.model.UserData;
import com.interviewdeck.mcp.service.FirebaseUserService;
import com.interviewdeck.mcp.service.QuestionService;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.*;
import java.util.stream.Collectors;

@Configuration
public class McpToolConfig {

    @Bean
    public WebMvcStreamableServerTransportProvider mcpTransportProvider() {
        return WebMvcStreamableServerTransportProvider.builder()
                .mcpEndpoint("/mcp")
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> mcpRoutes(WebMvcStreamableServerTransportProvider transport) {
        return transport.getRouterFunction();
    }

    @Bean
    public McpSyncServer mcpServer(WebMvcStreamableServerTransportProvider transport,
                                    QuestionService questionService,
                                    FirebaseUserService userService) {
        return McpServer.sync(transport)
                .serverInfo("interviewdeck", "0.1.0")
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .tools(
                        listCategoriesTool(questionService),
                        searchQuestionsTool(questionService),
                        getExplanationTool(questionService),
                        getQuestionsByCategoryTool(questionService),
                        startMockInterviewTool(questionService, userService),
                        getUserProgressTool(userService),
                        getBookmarksTool(questionService, userService),
                        getNotesTool(userService),
                        getIncompleteQuestionsTool(questionService, userService),
                        getCompletedQuestionsTool(questionService, userService),
                        markQuestionCompleteTool(questionService, userService),
                        addNoteTool(questionService, userService)
                )
                .build();
    }

    private static JsonSchema schema(Map<String, Object> properties, List<String> required) {
        return new JsonSchema("object", properties, required, null, null, null);
    }

    private SyncToolSpecification listCategoriesTool(QuestionService qs) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("listCategories")
                        .description("List all available interview question categories with their labels, groups, and question counts")
                        .inputSchema(schema(Map.of(), List.of()))
                        .build())
                .callHandler((exchange, request) -> {
                    String result = qs.getCategories().stream()
                            .map(c -> "- %s (%s) — %d questions".formatted(c.label(), c.group(), c.count()))
                            .collect(Collectors.joining("\n"));
                    return new CallToolResult(result, false);
                })
                .build();
    }

    private SyncToolSpecification searchQuestionsTool(QuestionService qs) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("searchQuestions")
                        .description("Search interview questions by keyword, category, and/or difficulty")
                        .inputSchema(schema(Map.of(
                                "query", Map.of("type", "string", "description", "Search keyword"),
                                "category", Map.of("type", "string", "description", "Category ID e.g. java, angular, springboot"),
                                "difficulty", Map.of("type", "string", "description", "beginner, intermediate, or advanced"),
                                "limit", Map.of("type", "integer", "description", "Max results, default 10, max 50")
                        ), List.of("query")))
                        .build())
                .callHandler((exchange, request) -> {
                    var args = request.arguments();
                    String query = (String) args.get("query");
                    String category = (String) args.get("category");
                    String difficulty = (String) args.get("difficulty");
                    int limit = args.containsKey("limit") ? Math.min(((Number) args.get("limit")).intValue(), 50) : 10;

                    List<Question> results = qs.searchQuestions(query, category, difficulty, limit);
                    if (results.isEmpty()) return new CallToolResult("No questions found.", false);

                    StringBuilder sb = new StringBuilder("Found %d question(s):\n\n".formatted(results.size()));
                    for (Question q : results) {
                        sb.append("[%s] %s\n  Category: %s | Difficulty: %s\n".formatted(
                                q.id(), q.question(), q.category(), q.difficulty() != null ? q.difficulty() : "unset"));
                        if (q.tags() != null && !q.tags().isEmpty())
                            sb.append("  Tags: %s\n".formatted(String.join(", ", q.tags())));
                        sb.append("\n");
                    }
                    return new CallToolResult(sb.toString(), false);
                })
                .build();
    }

    private SyncToolSpecification getExplanationTool(QuestionService qs) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("getExplanation")
                        .description("Get full answer/explanation for a question by ID, including code examples and tips")
                        .inputSchema(schema(Map.of(
                                "questionId", Map.of("type", "string", "description", "The question ID e.g. java-oops")
                        ), List.of("questionId")))
                        .build())
                .callHandler((exchange, request) -> {
                    String qid = (String) request.arguments().get("questionId");
                    return qs.getQuestion(qid)
                            .map(q -> {
                                StringBuilder sb = new StringBuilder();
                                sb.append("## %s\n\nCategory: %s | Difficulty: %s\n\n".formatted(
                                        q.question(), q.category(), q.difficulty() != null ? q.difficulty() : "unset"));
                                sb.append("### Answer\n%s\n\n".formatted(stripHtml(q.answer())));
                                if (q.code() != null && !q.code().isBlank())
                                    sb.append("### Code\n```%s\n%s\n```\n\n".formatted(q.lang() != null ? q.lang() : "", q.code()));
                                if (q.tip() != null && !q.tip().isBlank())
                                    sb.append("Tip: %s\n\n".formatted(q.tip()));
                                if (q.deep() != null && !q.deep().isBlank())
                                    sb.append("### Deep Dive\n%s\n".formatted(stripHtml(q.deep())));
                                return new CallToolResult(sb.toString(), false);
                            })
                            .orElse(new CallToolResult("Question not found: " + qid, true));
                })
                .build();
    }

    private SyncToolSpecification startMockInterviewTool(QuestionService qs, FirebaseUserService us) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("startMockInterview")
                        .description("Start a mock interview with random questions from a category")
                        .inputSchema(schema(Map.of(
                                "category", Map.of("type", "string", "description", "Category ID e.g. java, angular"),
                                "difficulty", Map.of("type", "string", "description", "beginner, intermediate, or advanced"),
                                "count", Map.of("type", "integer", "description", "Number of questions, default 5, max 20"),
                                "userId", Map.of("type", "string", "description", "Firebase UID or email address to exclude completed questions")
                        ), List.of("category")))
                        .build())
                .callHandler((exchange, request) -> {
                    var args = request.arguments();
                    String category = (String) args.get("category");
                    String difficulty = (String) args.get("difficulty");
                    int count = args.containsKey("count") ? Math.min(((Number) args.get("count")).intValue(), 20) : 5;

                    Set<String> excludeIds = Collections.emptySet();
                    String userIdOrEmail = (String) args.get("userId");
                    if (userIdOrEmail != null && !userIdOrEmail.isBlank()) {
                        String uid = resolveOrError(us, userIdOrEmail);
                        if (uid != null) excludeIds = new HashSet<>(us.getUserData(uid).progress());
                    }

                    List<Question> questions = qs.getRandomQuestions(category, difficulty, count, excludeIds);
                    if (questions.isEmpty()) return new CallToolResult("No questions available.", false);

                    StringBuilder sb = new StringBuilder("Mock Interview: %s\n%d questions\n\n".formatted(
                            category.toUpperCase(), questions.size()));
                    for (int i = 0; i < questions.size(); i++) {
                        Question q = questions.get(i);
                        sb.append("Q%d (ID: %s): %s\n".formatted(i + 1, q.id(), q.question()));
                        if (q.difficulty() != null) sb.append("  Difficulty: %s\n".formatted(q.difficulty()));
                        sb.append("\n");
                    }
                    sb.append("Ask me to explain any question by its ID.");
                    return new CallToolResult(sb.toString(), false);
                })
                .build();
    }

    private SyncToolSpecification getUserProgressTool(FirebaseUserService us) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("getUserProgress")
                        .description("Get user's interview preparation progress: completed count, bookmarks, notes")
                        .inputSchema(schema(Map.of(
                                "userId", Map.of("type", "string", "description", "Firebase UID or email address")
                        ), List.of("userId")))
                        .build())
                .callHandler((exchange, request) -> {
                    String uid = resolveOrError(us, (String) request.arguments().get("userId"));
                    if (uid == null) return new CallToolResult("Could not find user. Check the email or UID.", true);
                    UserData data = us.getUserData(uid);
                    StringBuilder sb = new StringBuilder("InterviewDeck Progress\n\n");
                    sb.append("- Completed: %d\n- Bookmarked: %d\n- Notes: %d\n\n".formatted(
                            data.progress().size(), data.bookmarks().size(), data.notes().size()));
                    if (!data.progress().isEmpty()) {
                        sb.append("By Category:\n");
                        data.progress().stream()
                                .map(id -> { int d = id.indexOf('-'); return d > 0 ? id.substring(0, d) : id; })
                                .collect(Collectors.groupingBy(c -> c, Collectors.counting()))
                                .entrySet().stream()
                                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                                .forEach(e -> sb.append("- %s: %d\n".formatted(e.getKey(), e.getValue())));
                    }
                    return new CallToolResult(sb.toString(), false);
                })
                .build();
    }

    private SyncToolSpecification getBookmarksTool(QuestionService qs, FirebaseUserService us) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("getBookmarks")
                        .description("Get user's bookmarked interview questions")
                        .inputSchema(schema(Map.of(
                                "userId", Map.of("type", "string", "description", "Firebase UID or email address")
                        ), List.of("userId")))
                        .build())
                .callHandler((exchange, request) -> {
                    String uid = resolveOrError(us, (String) request.arguments().get("userId"));
                    if (uid == null) return new CallToolResult("Could not find user. Check the email or UID.", true);
                    UserData data = us.getUserData(uid);
                    if (data.bookmarks().isEmpty()) return new CallToolResult("No bookmarks yet.", false);
                    List<Question> bookmarked = qs.getQuestionsByIds(data.bookmarks());
                    StringBuilder sb = new StringBuilder("Bookmarked Questions (%d)\n\n".formatted(bookmarked.size()));
                    for (Question q : bookmarked)
                        sb.append("- [%s] %s (%s)\n".formatted(q.id(), q.question(), q.category()));
                    return new CallToolResult(sb.toString(), false);
                })
                .build();
    }

    private SyncToolSpecification getNotesTool(FirebaseUserService us) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("getNotes")
                        .description("Get user's study notes, optionally for a specific question")
                        .inputSchema(schema(Map.of(
                                "userId", Map.of("type", "string", "description", "Firebase UID or email address"),
                                "questionId", Map.of("type", "string", "description", "Optional question ID")
                        ), List.of("userId")))
                        .build())
                .callHandler((exchange, request) -> {
                    var args = request.arguments();
                    String uid = resolveOrError(us, (String) args.get("userId"));
                    if (uid == null) return new CallToolResult("Could not find user. Check the email or UID.", true);
                    UserData data = us.getUserData(uid);
                    if (data.notes().isEmpty()) return new CallToolResult("No notes yet.", false);
                    String qid = (String) args.get("questionId");
                    if (qid != null && !qid.isBlank()) {
                        String note = data.notes().get(qid);
                        return new CallToolResult(note != null ? "Note for %s: %s".formatted(qid, note) : "No note for: " + qid, false);
                    }
                    StringBuilder sb = new StringBuilder("Notes (%d)\n\n".formatted(data.notes().size()));
                    data.notes().forEach((id, note) -> sb.append("%s: %s\n\n".formatted(id,
                            note.length() > 100 ? note.substring(0, 100) + "..." : note)));
                    return new CallToolResult(sb.toString(), false);
                })
                .build();
    }

    private SyncToolSpecification getIncompleteQuestionsTool(QuestionService qs, FirebaseUserService us) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("getIncompleteQuestions")
                        .description("Get questions the user has NOT completed in a category. Use this to continue learning — fetches remaining questions to study.")
                        .inputSchema(schema(Map.of(
                                "userId", Map.of("type", "string", "description", "Firebase UID or email address"),
                                "category", Map.of("type", "string", "description", "Category ID e.g. java, angular"),
                                "difficulty", Map.of("type", "string", "description", "Optional: beginner, intermediate, or advanced"),
                                "limit", Map.of("type", "integer", "description", "Max results, default 10")
                        ), List.of("userId", "category")))
                        .build())
                .callHandler((exchange, request) -> {
                    var args = request.arguments();
                    String category = (String) args.get("category");
                    int limit = args.containsKey("limit") ? Math.min(((Number) args.get("limit")).intValue(), 50) : 10;
                    String uid = resolveOrError(us, (String) args.get("userId"));
                    if (uid == null) return new CallToolResult("Could not find user. Check the email or UID.", true);
                    Set<String> completed = new HashSet<>(us.getUserData(uid).progress());

                    String difficulty = (String) args.get("difficulty");
                    List<Question> incomplete = qs.searchQuestions(null, category, difficulty, 1000).stream()
                            .filter(q -> !completed.contains(q.id()))
                            .limit(limit)
                            .toList();
                    if (incomplete.isEmpty())
                        return new CallToolResult("All done in %s!".formatted(category), false);

                    StringBuilder sb = new StringBuilder("Incomplete %s (%d)\n\n".formatted(category, incomplete.size()));
                    for (Question q : incomplete)
                        sb.append("- [%s] %s (%s)\n".formatted(q.id(), q.question(), q.difficulty() != null ? q.difficulty() : "unset"));
                    return new CallToolResult(sb.toString(), false);
                })
                .build();
    }

    private SyncToolSpecification getQuestionsByCategoryTool(QuestionService qs) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("getQuestionsByCategory")
                        .description("Get all questions in a category, optionally filtered by difficulty level (beginner, intermediate, advanced)")
                        .inputSchema(schema(Map.of(
                                "category", Map.of("type", "string", "description", "Category ID e.g. java, angular, springboot"),
                                "difficulty", Map.of("type", "string", "description", "beginner, intermediate, or advanced"),
                                "limit", Map.of("type", "integer", "description", "Max results, default 20, max 100")
                        ), List.of("category")))
                        .build())
                .callHandler((exchange, request) -> {
                    var args = request.arguments();
                    String category = (String) args.get("category");
                    String difficulty = (String) args.get("difficulty");
                    int limit = args.containsKey("limit") ? Math.min(((Number) args.get("limit")).intValue(), 100) : 20;

                    List<Question> results = qs.searchQuestions(null, category, difficulty, limit);
                    if (results.isEmpty()) return new CallToolResult("No questions found for %s.".formatted(category), false);

                    StringBuilder sb = new StringBuilder("%s Questions".formatted(category.toUpperCase()));
                    if (difficulty != null) sb.append(" (%s)".formatted(difficulty));
                    sb.append(" — %d results\n\n".formatted(results.size()));
                    for (Question q : results)
                        sb.append("- [%s] %s | %s\n".formatted(q.id(), q.question(), q.difficulty() != null ? q.difficulty() : "unset"));
                    return new CallToolResult(sb.toString(), false);
                })
                .build();
    }

    private SyncToolSpecification getCompletedQuestionsTool(QuestionService qs, FirebaseUserService us) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("getCompletedQuestions")
                        .description("Get questions the user has completed, optionally filtered by category. Use this to quiz/test the user on topics they've already studied.")
                        .inputSchema(schema(Map.of(
                                "userId", Map.of("type", "string", "description", "Firebase UID or email address"),
                                "category", Map.of("type", "string", "description", "Optional category ID to filter e.g. angular, java"),
                                "limit", Map.of("type", "integer", "description", "Max results, default 10")
                        ), List.of("userId")))
                        .build())
                .callHandler((exchange, request) -> {
                    var args = request.arguments();
                    String uid = resolveOrError(us, (String) args.get("userId"));
                    if (uid == null) return new CallToolResult("Could not find user. Check the email or UID.", true);
                    int limit = args.containsKey("limit") ? Math.min(((Number) args.get("limit")).intValue(), 50) : 10;
                    String category = (String) args.get("category");

                    UserData data = us.getUserData(uid);
                    if (data.progress().isEmpty()) return new CallToolResult("No completed questions yet.", false);

                    List<Question> completed = qs.getQuestionsByIds(data.progress());
                    if (category != null)
                        completed = completed.stream().filter(q -> q.category().equalsIgnoreCase(category)).toList();

                    if (completed.isEmpty())
                        return new CallToolResult("No completed questions in %s.".formatted(category != null ? category : "any category"), false);

                    List<Question> limited = completed.stream().limit(limit).toList();
                    StringBuilder sb = new StringBuilder("Completed Questions (%d of %d)\n\n".formatted(limited.size(), completed.size()));
                    for (Question q : limited)
                        sb.append("- [%s] %s | %s | %s\n".formatted(q.id(), q.question(), q.category(), q.difficulty() != null ? q.difficulty() : "unset"));
                    return new CallToolResult(sb.toString(), false);
                })
                .build();
    }

    private SyncToolSpecification markQuestionCompleteTool(QuestionService qs, FirebaseUserService us) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("markQuestionComplete")
                        .description("Mark one or more questions as completed for the user. Updates the user's progress on interviewdeck.in. You can pass question IDs directly, OR pass a search keyword/question text and the server will find the matching question automatically.")
                        .inputSchema(schema(Map.of(
                                "userId", Map.of("type", "string", "description", "Firebase UID or email address"),
                                "questionId", Map.of("type", "string", "description", "Question ID(s) comma-separated e.g. angular-lifecycle,angular-di. Leave empty if using searchText."),
                                "searchText", Map.of("type", "string", "description", "Search keyword or question text to find and mark complete. Use this when the user refers to a question by its text instead of ID.")
                        ), List.of("userId")))
                        .build())
                .callHandler((exchange, request) -> {
                    var args = request.arguments();
                    String uid = resolveOrError(us, (String) args.get("userId"));
                    if (uid == null) return new CallToolResult("Could not find user. Check the email or UID.", true);

                    List<String> ids = new ArrayList<>();
                    String raw = (String) args.get("questionId");
                    if (raw != null && !raw.isBlank())
                        ids.addAll(Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());

                    String searchText = (String) args.get("searchText");
                    if (ids.isEmpty() && searchText != null && !searchText.isBlank()) {
                        Optional<Question> found = qs.findByText(searchText);
                        if (found.isEmpty()) return new CallToolResult("Could not find a question matching: " + searchText, true);
                        ids.add(found.get().id());
                    }

                    if (ids.isEmpty()) return new CallToolResult("Provide questionId or searchText.", true);

                    boolean ok = ids.size() == 1
                            ? us.markQuestionComplete(uid, ids.get(0))
                            : us.markQuestionsComplete(uid, ids);

                    if (ok) {
                        List<String> names = ids.stream()
                                .map(id -> qs.getQuestion(id).map(q -> "\"%s\" (%s)".formatted(q.question(), id)).orElse(id))
                                .toList();
                        return new CallToolResult("Marked %d question(s) as complete:\n%s\nSynced to your InterviewDeck account.".formatted(ids.size(), String.join("\n", names)), false);
                    }
                    return new CallToolResult("Failed to update progress. Please try again.", true);
                })
                .build();
    }

    private SyncToolSpecification addNoteTool(QuestionService qs, FirebaseUserService us) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("addNote")
                        .description("Add or update a study note for a question. The note is saved to the user's InterviewDeck account. You can pass questionId directly OR pass searchText to find the question by its text/keyword. Use this when the user says 'save this as a note' or 'add this to my notes'.")
                        .inputSchema(schema(Map.of(
                                "userId", Map.of("type", "string", "description", "Firebase UID or email address"),
                                "questionId", Map.of("type", "string", "description", "The question ID. Leave empty if using searchText."),
                                "searchText", Map.of("type", "string", "description", "Search keyword or question text to find the question. Use when the user refers to a question by its content."),
                                "note", Map.of("type", "string", "description", "The note content to save")
                        ), List.of("userId", "note")))
                        .build())
                .callHandler((exchange, request) -> {
                    var args = request.arguments();
                    String uid = resolveOrError(us, (String) args.get("userId"));
                    if (uid == null) return new CallToolResult("Could not find user. Check the email or UID.", true);
                    String note = (String) args.get("note");

                    String qid = (String) args.get("questionId");
                    String searchText = (String) args.get("searchText");

                    Question matched;
                    if (qid != null && !qid.isBlank()) {
                        Optional<Question> q = qs.getQuestion(qid);
                        if (q.isEmpty()) return new CallToolResult("Question not found: " + qid, true);
                        matched = q.get();
                    } else if (searchText != null && !searchText.isBlank()) {
                        Optional<Question> q = qs.findByText(searchText);
                        if (q.isEmpty()) return new CallToolResult("Could not find a question matching: " + searchText, true);
                        matched = q.get();
                    } else {
                        return new CallToolResult("Provide questionId or searchText to identify the question.", true);
                    }

                    boolean ok = us.addNote(uid, matched.id(), note);
                    if (ok)
                        return new CallToolResult("Note saved for \"%s\" (%s).\nSynced to your InterviewDeck account.".formatted(matched.question(), matched.id()), false);
                    return new CallToolResult("Failed to save note. Please try again.", true);
                })
                .build();
    }

    private static String resolveOrError(FirebaseUserService us, String userIdOrEmail) {
        return us.resolveUid(userIdOrEmail);
    }

    private static String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", "")
                .replaceAll("&amp;", "&").replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"").replaceAll("&#39;", "'").replaceAll("&nbsp;", " ")
                .trim();
    }
}
