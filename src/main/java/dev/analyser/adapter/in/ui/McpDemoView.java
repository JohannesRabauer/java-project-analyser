package dev.analyser.adapter.in.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import dev.analyser.adapter.in.mcp.McpDispatcher;
import dev.analyser.adapter.out.persistence.AnalysisJobRepository;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.*;

@Route("")
public class McpDemoView extends VerticalLayout {

    private final McpDispatcher mcpDispatcher;
    private final AnalysisJobRepository jobRepository;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final VerticalLayout chatArea;
    private final TextField inputField;
    private final Button sendButton;
    private final VerticalLayout sidebar;
    private UUID currentJobId;

    private Checkbox useLlmCheckbox;
    private Checkbox showProtocolCheckbox;

    @Inject
    Instance<ChatModel> chatLanguageModelInstance;

    public McpDemoView(McpDispatcher mcpDispatcher, AnalysisJobRepository jobRepository) {
        this.mcpDispatcher = mcpDispatcher;
        this.jobRepository = jobRepository;

        setSizeFull();
        setSpacing(false);
        setPadding(false);
        getStyle().set("background-color", "#0f172a");
        getStyle().set("color", "#f8fafc");
        getStyle().set("font-family", "'Outfit', 'Inter', sans-serif");

        HorizontalLayout mainLayout = new HorizontalLayout();
        mainLayout.setSizeFull();
        mainLayout.setSpacing(false);

        sidebar = createSidebar();
        mainLayout.add(sidebar);

        VerticalLayout chatContainer = new VerticalLayout();
        chatContainer.setSizeFull();
        chatContainer.setPadding(true);
        chatContainer.setSpacing(true);
        chatContainer.getStyle().set("background", "radial-gradient(circle at top right, #1e293b, #0f172a)");

        H1 title = new H1("Java Project Analyser");
        title.getStyle().set("font-size", "1.8rem");
        title.getStyle().set("font-weight", "800");
        title.getStyle().set("background", "linear-gradient(to right, #38bdf8, #818cf8)");
        title.getStyle().set("-webkit-background-clip", "text");
        title.getStyle().set("-webkit-text-fill-color", "transparent");
        title.getStyle().set("margin", "0");
        title.getStyle().set("padding-bottom", "10px");
        title.getStyle().set("border-bottom", "1px solid #334155");
        chatContainer.add(title);

        chatArea = new VerticalLayout();
        chatArea.setSizeFull();
        chatArea.getStyle().set("overflow-y", "auto");
        chatArea.getStyle().set("padding", "10px");
        chatArea.setSpacing(true);

        addAssistantMessage("Hello! I am your Java Project Analyser AI Assistant. I communicate with the backend MCP server using LangChain4J.\n\nTo get started, click **Analyse Current Project** in the sidebar to run the 10-phase pipeline!");

        chatContainer.add(chatArea);

        HorizontalLayout inputLayout = new HorizontalLayout();
        inputLayout.setWidthFull();
        inputLayout.setSpacing(true);
        inputLayout.setPadding(false);

        inputField = new TextField();
        inputField.setPlaceholder("Ask a question, search codebase, or run MCP tools...");
        inputField.setWidthFull();
        inputField.getStyle().set("background-color", "#1e293b");
        inputField.getStyle().set("border", "1px solid #475569");
        inputField.getStyle().set("border-radius", "12px");
        inputField.getStyle().set("color", "#f8fafc");
        inputField.getStyle().set("padding", "8px 16px");

        sendButton = new Button(new Icon(VaadinIcon.PAPERPLANE));
        sendButton.getStyle().set("background", "linear-gradient(135deg, #6366f1, #4f46e5)");
        sendButton.getStyle().set("color", "#ffffff");
        sendButton.getStyle().set("border", "none");
        sendButton.getStyle().set("border-radius", "12px");
        sendButton.getStyle().set("cursor", "pointer");
        sendButton.getStyle().set("transition", "transform 0.2s, box-shadow 0.2s");
        sendButton.addClickListener(e -> sendMessage());
        inputField.addKeyPressListener(Key.ENTER, e -> sendMessage());

        inputLayout.add(inputField, sendButton);
        chatContainer.add(inputLayout);

        mainLayout.add(chatContainer);
        add(mainLayout);
    }

    private VerticalLayout createSidebar() {
        VerticalLayout layout = new VerticalLayout();
        layout.setWidth("320px");
        layout.setHeightFull();
        layout.getStyle().set("background-color", "#0b0f19");
        layout.getStyle().set("border-right", "1px solid #1e293b");
        layout.getStyle().set("padding", "24px");
        layout.setSpacing(true);

        H3 mcpTitle = new H3("MCP Server Status");
        mcpTitle.getStyle().set("color", "#38bdf8");
        mcpTitle.getStyle().set("font-weight", "700");
        mcpTitle.getStyle().set("margin-top", "0");
        layout.add(mcpTitle);

        HorizontalLayout statusLayout = new HorizontalLayout();
        statusLayout.setAlignItems(Alignment.CENTER);
        Div dot = new Div();
        dot.setWidth("10px");
        dot.setHeight("10px");
        dot.getStyle().set("border-radius", "50%");
        dot.getStyle().set("background-color", "#10b981");
        dot.getStyle().set("box-shadow", "0 0 10px #10b981");

        Span statusText = new Span("ACTIVE (SSE/Stdio)");
        statusText.getStyle().set("font-size", "0.85rem");
        statusText.getStyle().set("font-weight", "600");
        statusText.getStyle().set("color", "#10b981");

        statusLayout.add(dot, statusText);
        layout.add(statusLayout);

        Button runAnalysis = new Button("Analyse Current Project", new Icon(VaadinIcon.PLAY));
        runAnalysis.setWidthFull();
        runAnalysis.getStyle().set("background", "linear-gradient(135deg, #0284c7, #0369a1)");
        runAnalysis.getStyle().set("color", "#ffffff");
        runAnalysis.getStyle().set("border", "none");
        runAnalysis.getStyle().set("border-radius", "8px");
        runAnalysis.getStyle().set("padding", "10px");
        runAnalysis.getStyle().set("font-weight", "600");
        runAnalysis.addClickListener(e -> triggerLocalAnalysis());

        Button showSummary = new Button("Show Summary", new Icon(VaadinIcon.INFO_CIRCLE));
        showSummary.setWidthFull();
        showSummary.getStyle().set("background-color", "#1e293b");
        showSummary.getStyle().set("color", "#f8fafc");
        showSummary.getStyle().set("border", "1px solid #334155");
        showSummary.getStyle().set("border-radius", "8px");
        showSummary.addClickListener(e -> askForSummary());

        Button viewReport = new Button("View Ascii Report", new Icon(VaadinIcon.FILE_TEXT));
        viewReport.setWidthFull();
        viewReport.getStyle().set("background-color", "#1e293b");
        viewReport.getStyle().set("color", "#f8fafc");
        viewReport.getStyle().set("border", "1px solid #334155");
        viewReport.getStyle().set("border-radius", "8px");
        viewReport.addClickListener(e -> askForReport());

        layout.add(runAnalysis, showSummary, viewReport);

        H3 configTitle = new H3("AI Client Config");
        configTitle.getStyle().set("color", "#38bdf8");
        configTitle.getStyle().set("font-weight", "700");
        configTitle.getStyle().set("margin-top", "20px");
        layout.add(configTitle);

        useLlmCheckbox = new Checkbox("Use LangChain4J LLM", false);
        useLlmCheckbox.getStyle().set("color", "#f8fafc");
        useLlmCheckbox.addValueChangeListener(e -> {
            if (e.getValue() && (chatLanguageModelInstance == null || chatLanguageModelInstance.isUnsatisfied())) {
                addAssistantMessage("⚠️ *No active LangChain4J ChatModel found in Quarkus CDI context. Please configure OpenAI or Ollama in application.properties! Falling back to Local Semantic mode...*");
                useLlmCheckbox.setValue(false);
            }
        });

        showProtocolCheckbox = new Checkbox("Show JSON-RPC Traffic", true);
        showProtocolCheckbox.getStyle().set("color", "#f8fafc");

        layout.add(useLlmCheckbox, showProtocolCheckbox);

        Div toolsHeader = new Div(new Text("Exposed MCP Tools:"));
        toolsHeader.getStyle().set("font-weight", "700");
        toolsHeader.getStyle().set("font-size", "0.9rem");
        toolsHeader.getStyle().set("color", "#94a3b8");
        toolsHeader.getStyle().set("margin-top", "15px");
        layout.add(toolsHeader);

        String[] tools = {"analyse_project", "get_analysis_status", "search_codebase", "get_project_summary", "get_ascii_report"};
        for (String tool : tools) {
            HorizontalLayout tLayout = new HorizontalLayout();
            tLayout.setSpacing(true);
            Icon tIcon = new Icon(VaadinIcon.COGS);
            tIcon.setSize("14px");
            tIcon.getStyle().set("color", "#818cf8");
            Span tSpan = new Span(tool);
            tSpan.getStyle().set("font-family", "monospace");
            tSpan.getStyle().set("font-size", "0.8rem");
            tLayout.add(tIcon, tSpan);
            layout.add(tLayout);
        }

        return layout;
    }

    private void sendMessage() {
        String query = inputField.getValue().trim();
        if (query.isEmpty()) return;

        addUserMessage(query);
        inputField.clear();

        if (currentJobId == null && !query.toLowerCase().contains("analyse") && !query.toLowerCase().contains("analyze")) {
            addAssistantMessage("No analysis job is currently loaded. Please click **Analyse Current Project** first to register a project and trigger the analysis pipeline!");
            return;
        }

        if (useLlmCheckbox.getValue() && chatLanguageModelInstance != null && chatLanguageModelInstance.isResolvable()) {
            executeLlmMcpFlow(query);
        } else {
            executeLocalSemanticMcpFlow(query);
        }
    }

    private void executeLlmMcpFlow(String query) {
        addAssistantMessage("🤖 *[LangChain4J Client] Analyzing request using real LLM and scanning for available MCP Tools...*");
        try {
            ChatModel model = chatLanguageModelInstance.get();
            List<ToolSpecification> toolSpecs = getMcpToolSpecifications();
            List<ChatMessage> chatMessages = new ArrayList<>();
            chatMessages.add(new UserMessage("You are an expert AI system assisting with Java project analysis. Use the provided tools to retrieve analysis jobs, project summaries, and codebase search results. Here is the user's request: " + query));

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(chatMessages)
                    .toolSpecifications(toolSpecs)
                    .build();
            ChatResponse chatResponse = model.chat(chatRequest);
            AiMessage aiMessage = chatResponse.aiMessage();

            if (aiMessage.hasToolExecutionRequests()) {
                for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                    String toolName = toolRequest.name();
                    String argumentsStr = toolRequest.arguments();
                    addAssistantMessage("🔧 *[LangChain4J Client] LLM requested tool execution: '" + toolName + "'*");

                    UUID id = UUID.randomUUID();
                    String reqJson = String.format("{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"method\":\"tools/call\",\"params\":{\"name\":\"%s\",\"arguments\":%s}}", id, toolName, argumentsStr);

                    if (showProtocolCheckbox.getValue()) {
                        addJsonRpcTerminalLog("MCP CLIENT -> MCP SERVER (JSON-RPC Request)", reqJson);
                    }

                    String responseStr = mcpDispatcher.dispatch(reqJson);
                    if (showProtocolCheckbox.getValue()) {
                        addJsonRpcTerminalLog("MCP SERVER -> MCP CLIENT (JSON-RPC Response)", responseStr);
                    }

                    var root = mapper.readTree(responseStr);
                    String resultText = "";
                    if (root.has("result") && root.get("result").has("content")) {
                        var contentArray = root.get("result").get("content");
                        if (contentArray.isArray() && contentArray.size() > 0) {
                            resultText = contentArray.get(0).get("text").asText();
                        }
                    }

                    chatMessages.add(aiMessage);
                    chatMessages.add(new ToolExecutionResultMessage(toolRequest.id(), toolRequest.name(), resultText));
                    ChatResponse finalResponse = model.chat(chatMessages);
                    addAssistantMessage(finalResponse.aiMessage().text());
                }
            } else {
                addAssistantMessage(aiMessage.text());
            }
        } catch (Exception e) {
            addAssistantMessage("❌ LLM execution failed: " + e.getMessage() + ". Falling back to Local Semantic Mode.");
            executeLocalSemanticMcpFlow(query);
        }
    }

    private void executeLocalSemanticMcpFlow(String query) {
        String queryLower = query.toLowerCase();
        String toolName;
        String reqJson;
        UUID id = UUID.randomUUID();

        addAssistantMessage("⚡ *[LangChain4J Local Semantic Client] Selecting matching MCP Tool dynamically...*");

        if (queryLower.contains("analyse") || queryLower.contains("analyze") || queryLower.contains("start")) {
            toolName = "analyse_project";
            String currentPath = System.getProperty("user.dir").replace("\\", "/");
            reqJson = String.format("{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"method\":\"tools/call\",\"params\":{\"name\":\"%s\",\"arguments\":{\"projectPath\":\"%s\"}}}", id, toolName, currentPath);
        } else if (queryLower.contains("status") || queryLower.contains("progress") || queryLower.contains("state")) {
            toolName = "get_analysis_status";
            reqJson = String.format("{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"method\":\"tools/call\",\"params\":{\"name\":\"%s\",\"arguments\":{\"jobId\":\"%s\"}}}", id, toolName, currentJobId);
        } else if (queryLower.contains("summary") || queryLower.contains("executive") || queryLower.contains("purpose")) {
            toolName = "get_project_summary";
            reqJson = String.format("{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"method\":\"tools/call\",\"params\":{\"name\":\"%s\",\"arguments\":{\"jobId\":\"%s\"}}}", id, toolName, currentJobId);
        } else if (queryLower.contains("report") || queryLower.contains("ascii") || queryLower.contains("doc")) {
            toolName = "get_ascii_report";
            reqJson = String.format("{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"method\":\"tools/call\",\"params\":{\"name\":\"%s\",\"arguments\":{\"jobId\":\"%s\"}}}", id, toolName, currentJobId);
        } else {
            toolName = "search_codebase";
            reqJson = String.format("{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"method\":\"tools/call\",\"params\":{\"name\":\"%s\",\"arguments\":{\"jobId\":\"%s\",\"query\":\"%s\"}}}", id, toolName, currentJobId, query.replace("\"", "\\\""));
        }

        addAssistantMessage("🔧 *Invoking real MCP Server Tool: '" + toolName + "'*");
        if (showProtocolCheckbox.getValue()) {
            addJsonRpcTerminalLog("MCP CLIENT -> MCP SERVER (JSON-RPC Request)", reqJson);
        }

        try {
            String responseStr = mcpDispatcher.dispatch(reqJson);
            if (showProtocolCheckbox.getValue()) {
                addJsonRpcTerminalLog("MCP SERVER -> MCP CLIENT (JSON-RPC Response)", responseStr);
            }

            var root = mapper.readTree(responseStr);
            if (root.has("result") && root.get("result").has("content")) {
                var contentArray = root.get("result").get("content");
                if (contentArray.isArray() && contentArray.size() > 0) {
                    String resultText = contentArray.get(0).get("text").asText();
                    addAssistantMessage(resultText);
                    if ("analyse_project".equals(toolName)) {
                        int idIdx = resultText.indexOf("Job ID: ");
                        if (idIdx != -1) {
                            String uuidStr = resultText.substring(idIdx + 8, idIdx + 8 + 36);
                            currentJobId = UUID.fromString(uuidStr);
                        }
                    }
                }
            } else if (root.has("error")) {
                addAssistantMessage("❌ MCP Server Error: " + root.get("error").get("message").asText());
            }
        } catch (Exception e) {
            addAssistantMessage("❌ Error dispatching to MCP Server: " + e.getMessage());
        }
    }

    private List<ToolSpecification> getMcpToolSpecifications() {
        List<ToolSpecification> specs = new ArrayList<>();
        specs.add(ToolSpecification.builder()
                .name("analyse_project")
                .description("Ingests a Java project from the local filesystem and starts the 10-phase analysis and RAG indexing pipeline.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("projectPath", "The absolute path of the Java project on the filesystem")
                        .required("projectPath")
                        .build())
                .build());
        specs.add(ToolSpecification.builder()
                .name("get_analysis_status")
                .description("Retrieves the real-time status of a running or completed analysis job.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("jobId", "The unique UUID of the analysis job")
                        .required("jobId")
                        .build())
                .build());
        specs.add(ToolSpecification.builder()
                .name("search_codebase")
                .description("Queries the indexed knowledge base of a project using vector-based similarity search to locate relevant code chunks.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("jobId", "The unique UUID of the analysis job")
                        .addStringProperty("query", "The vector search query text")
                        .required("jobId", "query")
                        .build())
                .build());
        specs.add(ToolSpecification.builder()
                .name("get_project_summary")
                .description("Gets the high-level business purpose and classification of the project generated during Phase 1.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("jobId", "The unique UUID of the analysis job")
                        .required("jobId")
                        .build())
                .build());
        specs.add(ToolSpecification.builder()
                .name("get_ascii_report")
                .description("Gets the complete compiled AsciiDoc architecture and design report for the analyzed project.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("jobId", "The unique UUID of the analysis job")
                        .required("jobId")
                        .build())
                .build());
        return specs;
    }

    private void triggerLocalAnalysis() { sendMessageDirectly("Analyse current project"); }

    private void askForSummary() {
        if (currentJobId == null) { addAssistantMessage("Please run an analysis first!"); return; }
        sendMessageDirectly("Show project summary");
    }

    private void askForReport() {
        if (currentJobId == null) { addAssistantMessage("Please run an analysis first!"); return; }
        sendMessageDirectly("Show ascii report");
    }

    private void sendMessageDirectly(String text) {
        inputField.setValue(text);
        sendMessage();
    }

    private void addUserMessage(String text) {
        Div bubble = new Div(new Text(text));
        bubble.getStyle().set("background-color", "#4f46e5");
        bubble.getStyle().set("color", "#ffffff");
        bubble.getStyle().set("padding", "10px 16px");
        bubble.getStyle().set("border-radius", "16px 16px 4px 16px");
        bubble.getStyle().set("align-self", "flex-end");
        bubble.getStyle().set("max-width", "70%");
        bubble.getStyle().set("word-break", "break-word");
        chatArea.add(bubble);
        chatArea.getElement().callJsFunction("scrollTop = this.scrollHeight");
    }

    private void addAssistantMessage(String text) {
        String formattedText = text.replace("\n", "<br>");
        Div bubble = new Div();
        Html html = new Html("<div>" + formattedText + "</div>");
        bubble.add(html);
        bubble.getStyle().set("background-color", "#1e293b");
        bubble.getStyle().set("color", "#f1f5f9");
        bubble.getStyle().set("padding", "12px 18px");
        bubble.getStyle().set("border-radius", "16px 16px 16px 4px");
        bubble.getStyle().set("align-self", "flex-start");
        bubble.getStyle().set("max-width", "85%");
        bubble.getStyle().set("border", "1px solid #334155");
        bubble.getStyle().set("word-break", "break-word");
        chatArea.add(bubble);
        chatArea.getElement().callJsFunction("scrollTop = this.scrollHeight");
    }

    private void addJsonRpcTerminalLog(String headerText, String json) {
        String prettyJson;
        try {
            Object obj = mapper.readValue(json, Object.class);
            prettyJson = mapper.writeValueAsString(obj);
        } catch (Exception e) { prettyJson = json; }

        String terminalHtml = String.format("""
            <div style="background-color: #0b0f19; border: 1px solid #38bdf8; border-radius: 8px; margin: 8px 0; width: 100%%; font-family: monospace; overflow: hidden; box-shadow: 0 4px 12px rgba(56, 189, 248, 0.15);">
                <div style="background-color: #1e293b; padding: 6px 12px; border-bottom: 1px solid #334155; display: flex; align-items: center; justify-content: space-between;">
                    <div style="display: flex; gap: 6px;">
                        <span style="width: 10px; height: 10px; border-radius: 50%%; background-color: #ef4444; display: inline-block;"></span>
                        <span style="width: 10px; height: 10px; border-radius: 50%%; background-color: #f59e0b; display: inline-block;"></span>
                        <span style="width: 10px; height: 10px; border-radius: 50%%; background-color: #10b981; display: inline-block;"></span>
                    </div>
                    <span style="color: #38bdf8; font-size: 0.75rem; font-weight: bold;">%s</span>
                </div>
                <pre style="margin: 0; padding: 12px; color: #34d399; font-size: 0.8rem; overflow-x: auto; white-space: pre-wrap; word-break: break-all;">%s</pre>
            </div>
            """, headerText, prettyJson.replace("<", "&lt;").replace(">", "&gt;"));

        Div logContainer = new Div();
        logContainer.setWidthFull();
        logContainer.getStyle().set("align-self", "flex-start");
        Html html = new Html("<div>" + terminalHtml + "</div>");
        logContainer.add(html);
        chatArea.add(logContainer);
        chatArea.getElement().callJsFunction("scrollTop = this.scrollHeight");
    }
}
