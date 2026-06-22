# Java Project Analyser

A local MCP server that deeply understands Java projects. Start it, point it at your codebase, and any MCP-capable AI agent (Claude Code, Cursor, VS Code Copilot) can query rich structural and semantic knowledge — class relationships, architecture patterns, potential bugs, security issues, and more.

## Quickstart (3 steps)

```sh
# 1. Copy the config template
cp .env.example .env

# 2. Edit .env — set your project URL and OpenAI key
#    PROJECT_URL=https://github.com/your-org/your-java-project
#    OPENAI_API_KEY=sk-...

# 3. Start everything
docker compose up
```

That's it. The system will:
1. Build the analyser (first run only, ~3 min)
2. Start PostgreSQL + the MCP server
3. Clone your project and run the analysis pipeline (~5 min for a 400-class project)
4. Serve the MCP API at `http://localhost:8080/mcp/sse`

Open `http://localhost:8080` to see progress and get connection instructions.

## Connect Your Agent

### Claude Code

Add to your `~/.claude/mcp.json`:

```json
{
  "mcpServers": {
    "java-analyser": {
      "url": "http://localhost:8080/mcp/sse"
    }
  }
}
```

### Cursor

Add to `.cursor/mcp.json`:

```json
{
  "mcpServers": {
    "java-analyser": {
      "url": "http://localhost:8080/mcp/sse"
    }
  }
}
```

## How It Works

1. **AST Parsing** — JavaParser extracts classes, methods, fields, annotations, and builds a dependency graph
2. **LLM Analysis** — GPT-4o-mini generates project summaries, class purposes, and architecture assessments
3. **Static Analysis** — Checkstyle, PMD, SpotBugs, and OWASP rules detect code quality and security issues
4. **RAG Indexing** — All knowledge is embedded and stored in pgvector for semantic search
5. **MCP Server** — 15 tools exposed via standard MCP protocol for any AI agent to use

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
| `get_checkstyle_warnings` | Code style issues (Checkstyle/PMD rules) |
| `get_security_report` | OWASP Top 10 security findings |
| `get_dependency_vulnerabilities` | Known CVEs in dependencies |
| `get_test_coverage_gaps` | Untested classes/methods |

### On-demand (LLM-powered, heavier)

| Tool | Description |
|------|-------------|
| `search_codebase` | Semantic search over indexed code |
| `get_possible_bugs` | LLM bug analysis (10-30s) |
| `suggest_improvement` | Refactoring suggestions (10-30s) |

## Configuration

The server supports separate model configuration for different concerns:

```properties
# Pipeline model (bulk analysis — can be cheap/local)
PIPELINE_MODEL_PROVIDER=openai        # or: ollama
PIPELINE_MODEL_NAME=gpt-4o-mini       # or: llama3

# Query model (on-demand reasoning — can be high-quality)
QUERY_MODEL_PROVIDER=openai
QUERY_MODEL_NAME=gpt-4o-mini          # or: gpt-4o for better quality

# Embedding model
EMBEDDING_PROVIDER=openai
EMBEDDING_MODEL_NAME=text-embedding-3-small
```

### Using Ollama (local, free)

```sh
# Start with Ollama profile
docker compose --profile ollama up

# Set in .env:
PIPELINE_MODEL_PROVIDER=ollama
PIPELINE_MODEL_NAME=llama3
EMBEDDING_PROVIDER=ollama
EMBEDDING_MODEL_NAME=nomic-embed-text
```

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
- MCP JSON-RPC 2.0 (SSE + stdio transport)
