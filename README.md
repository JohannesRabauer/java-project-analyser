# Java Project Analyser

A local MCP server that deeply understands Java projects. Start it, point it at your codebase, and any MCP-capable AI agent (Claude Code, Cursor, VS Code Copilot) can query rich structural and semantic knowledge — class relationships, architecture patterns, potential bugs, and more.

## How It Works

1. You start the server via Docker Compose
2. Your AI agent calls `analyse_project` to trigger deep analysis (JavaParser AST + LLM)
3. Results are stored as a searchable knowledge graph + RAG embeddings
4. Your agent queries pre-computed facts (instant) or on-demand LLM reasoning (10-30s)

## Quickstart

```sh
cp .env.example .env
# Edit .env to set your model preferences (see Configuration below)

docker compose --profile local up
```

This starts PostgreSQL (pgvector), Ollama, and the analyser server.

Once running:
- Landing page: http://localhost:8080/ (status + MCP integration instructions)
- MCP stdio: configure your agent to launch the server process directly
- MCP SSE: connect to `/mcp/sse` for HTTP-based transport

## Connecting Your Agent

### Claude Code

Add to your `mcp.json`:

```json
{
  "mcpServers": {
    "java-analyser": {
      "command": "docker",
      "args": ["exec", "-i", "java-project-analyser-app-1", "java", "-jar", "/app/analyser.jar", "--stdio"]
    }
  }
}
```

### Cursor

Add to `.cursor/mcp.json`:

```json
{
  "java-analyser": {
    "command": "docker",
    "args": ["exec", "-i", "java-project-analyser-app-1", "java", "-jar", "/app/analyser.jar", "--stdio"]
  }
}
```

## Configuration

The server supports separate model configuration for three concerns:

```properties
# Pipeline model (bulk analysis at index time — can be cheap/local)
analyser.pipeline.model-provider=ollama
analyser.pipeline.model-name=llama3

# Query model (on-demand reasoning at query time — can be high-quality)  
analyser.query.model-provider=openai
analyser.query.model-name=gpt-4o

# Embedding model
analyser.embedding.provider=ollama
analyser.embedding.model-name=nomic-embed-text
```

Set via environment variables or `application.properties`. Using the same model for all three is valid.

## MCP Tools

### Pre-computed (instant response)

| Tool | Description |
|------|-------------|
| `analyse_project` | Trigger analysis pipeline |
| `get_analysis_status` | Pipeline progress |
| `get_project_summary` | Executive summary, purpose, classification |
| `get_architecture_overview` | Layers, patterns, module boundaries |
| `get_class_purpose` | What a class does and why |
| `get_module_purpose` | Module responsibility |
| `get_related_classes` | Dependencies, dependents, inheritance |
| `get_class_diagram` | PlantUML diagram for a package |
| `get_checkstyle_warnings` | Code style issues |

### On-demand (LLM-powered, heavier)

| Tool | Description |
|------|-------------|
| `search_codebase` | Semantic search over indexed code |
| `get_possible_bugs` | LLM bug analysis (10-30s) |
| `suggest_improvement` | Refactoring suggestions (10-30s) |

## Local Development

```sh
./mvnw clean verify
./mvnw quarkus:dev
```

Quarkus Dev Services auto-starts PostgreSQL (pgvector) for development.

## Tech Stack

- Quarkus 3.x (Java 21)
- JavaParser (AST extraction — the heart of the system)
- LangChain4J (LLM integration — Ollama + OpenAI)
- PostgreSQL + pgvector (RAG storage)
- jOOQ (database access)
- Thymeleaf (landing page)
- MCP JSON-RPC 2.0 (stdio + SSE transport)
