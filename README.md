# InterviewDeck MCP Server

A Spring Boot MCP (Model Context Protocol) server that exposes InterviewDeck's interview preparation tools. Connect it to ChatGPT to interact with your question bank, progress, bookmarks, and notes through natural language.

## Tools Exposed

| Tool | Description |
|------|-------------|
| `listCategories` | List all question categories with counts |
| `searchQuestions` | Search questions by keyword, category, difficulty |
| `getExplanation` | Get full answer/explanation for a question |
| `startMockInterview` | Start a mock interview with random questions |
| `getUserProgress` | View completion stats and progress by category |
| `getBookmarks` | View bookmarked questions |
| `getNotes` | View personal study notes |
| `getIncompleteQuestions` | Find questions not yet completed |

## Setup

### Prerequisites
- Java 21+
- Maven 3.9+
- Firebase service account JSON (from Firebase Console → Project Settings → Service Accounts)

### 1. Copy question data
```bash
cp -r ../interview-helper-question-bank/*.json src/main/resources/questions/
```

### 2. Configure Firebase
Place your `firebase-service-account.json` in the project root, or set the environment variable:
```bash
export FIREBASE_CREDENTIALS_PATH=/path/to/firebase-service-account.json
```

### 3. Build & Run
```bash
mvn clean package
java -jar target/interviewdeck-mcp-server-0.1.0.jar
```

The MCP SSE endpoint will be available at: `http://localhost:8080/sse`

### 4. Connect to ChatGPT
1. Go to ChatGPT → Settings → Tools → Add custom tool
2. Enter your server URL: `https://your-domain.com/sse`
3. Set authentication (OAuth or API key)
4. ChatGPT will discover all InterviewDeck tools automatically

## Example Prompts (ChatGPT)
- "List all interview categories on InterviewDeck"
- "Search for Java multithreading questions"
- "Start an Angular mock interview with 10 questions"
- "Explain the question java-oops from InterviewDeck"
- "What Java questions haven't I completed yet?"
- "Show me my bookmarked questions"

## Deployment
For ChatGPT to reach your server, deploy it with a public HTTPS URL:
- **Railway** / **Render** / **Fly.io** — easy Spring Boot hosting
- **AWS EC2/ECS** — more control
- **ngrok** — for local development/testing

## Architecture
```
ChatGPT ←→ MCP (SSE) ←→ Spring Boot Server
                              ├── QuestionService (JSON files)
                              └── FirebaseUserService (Firestore)
```
