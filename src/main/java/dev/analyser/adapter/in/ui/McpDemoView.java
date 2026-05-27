package dev.analyser.adapter.in.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.dependency.CssImport;
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
import dev.analyser.domain.model.AnalysisJob;
import dev.analyser.domain.model.AnalysisStatus;
import java.util.Optional;
import java.util.UUID;

@Route("")
public class McpDemoView extends VerticalLayout {

    private final McpDispatcher mcpDispatcher;
    private final AnalysisJobRepository jobRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    private final VerticalLayout chatArea;
    private final TextField inputField;
    private final Button sendButton;
    private final VerticalLayout sidebar;
    private UUID currentJobId;

    public McpDemoView(McpDispatcher mcpDispatcher, AnalysisJobRepository jobRepository) {
        this.mcpDispatcher = mcpDispatcher;
        this.jobRepository = jobRepository;

        // Base layout setup
        setSizeFull();
        setSpacing(false);
        setPadding(false);
        getStyle().set("background-color", "#0f172a"); // sleek dark slate
        getStyle().set("color", "#f8fafc");
        getStyle().set("font-family", "'Outfit', 'Inter', sans-serif");

        // Horizontal Split-like Layout
        HorizontalLayout mainLayout = new HorizontalLayout();
        mainLayout.setSizeFull();
        mainLayout.setSpacing(false);

        // Sidebar Setup
        sidebar = createSidebar();
        mainLayout.add(sidebar);

        // Chat Container
        VerticalLayout chatContainer = new VerticalLayout();
        chatContainer.setSizeFull();
        chatContainer.setPadding(true);
        chatContainer.setSpacing(true);
        chatContainer.getStyle().set("background", "radial-gradient(circle at top right, #1e293b, #0f172a)");

        // Glowing Header
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

        // Chat Bubble Area
        chatArea = new VerticalLayout();
        chatArea.setSizeFull();
        chatArea.getStyle().set("overflow-y", "auto");
        chatArea.getStyle().set("padding", "10px");
        chatArea.setSpacing(true);

        // Initial welcome message
        addAssistantMessage("Hello! I am your Java Project Analyser AI Assistant. I communicate with the backend MCP server using LangChain4J.\n\nTo get started, click **Analyse Current Project** in the sidebar to run the 10-phase pipeline!");

        chatContainer.add(chatArea);

        // Input Layout
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

        // Status Indicator
        HorizontalLayout statusLayout = new HorizontalLayout();
        statusLayout.setAlignItems(Alignment.CENTER);
        Div dot = new Div();
        dot.setWidth("10px");
        dot.setHeight("10px");
        dot.getStyle().set("border-radius", "50%");
        dot.getStyle().set("background-color", "#10b981"); // bright green
        dot.getStyle().set("box-shadow", "0 0 10px #10b981");

        Span statusText = new Span("ACTIVE (SSE/Stdio)");
        statusText.getStyle().set("font-size", "0.85rem");
        statusText.getStyle().set("font-weight", "600");
        statusText.getStyle().set("color", "#10b981");

        statusLayout.add(dot, statusText);
        layout.add(statusLayout);

        // Action Buttons
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

        // Registered Tools List Display
        Div toolsHeader = new Div(new Text("Exposed MCP Tools:"));
        toolsHeader.getStyle().set("font-weight", "700");
        toolsHeader.getStyle().set("font-size", "0.9rem");
        toolsHeader.getStyle().set("color", "#94a3b8");
        toolsHeader.getStyle().set("margin-top", "20px");
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
        if (query.isEmpty()) {
            return;
        }

        addUserMessage(query);
        inputField.clear();

        // Process message via MCP client simulation
        if (currentJobId == null) {
            addAssistantMessage("No analysis job is currently loaded. Please click **Analyse Current Project** first to register a project and trigger the analysis pipeline!");
            return;
        }

        if (query.toLowerCase().contains("search") || query.toLowerCase().contains("find") || query.toLowerCase().contains("where")) {
            // Simulate MCP Client calling search_codebase tool
            addAssistantMessage("🔧 *[LangChain4J McpClient] Automatically invoking tool 'search_codebase'...*");
            String reqJson = String.format("{\"jsonrpc\":\"2.0\",\"id\":\"view_1\",\"method\":\"tools/call\",\"params\":{\"name\":\"search_codebase\",\"arguments\":{\"jobId\":\"%s\",\"query\":\"%s\"}}}",
                    currentJobId, query);
            executeAndDisplayMcp(reqJson);
        } else if (query.toLowerCase().contains("status") || query.toLowerCase().contains("progress")) {
            // Simulate get_analysis_status
            addAssistantMessage("🔧 *[LangChain4J McpClient] Automatically invoking tool 'get_analysis_status'...*");
            String reqJson = String.format("{\"jsonrpc\":\"2.0\",\"id\":\"view_2\",\"method\":\"tools/call\",\"params\":{\"name\":\"get_analysis_status\",\"arguments\":{\"jobId\":\"%s\"}}}",
                    currentJobId);
            executeAndDisplayMcp(reqJson);
        } else if (query.toLowerCase().contains("summary") || query.toLowerCase().contains("purpose")) {
            // Simulate get_project_summary
            addAssistantMessage("🔧 *[LangChain4J McpClient] Automatically invoking tool 'get_project_summary'...*");
            String reqJson = String.format("{\"jsonrpc\":\"2.0\",\"id\":\"view_3\",\"method\":\"tools/call\",\"params\":{\"name\":\"get_project_summary\",\"arguments\":{\"jobId\":\"%s\"}}}",
                    currentJobId);
            executeAndDisplayMcp(reqJson);
        } else if (query.toLowerCase().contains("report") || query.toLowerCase().contains("ascii")) {
            // Simulate get_ascii_report
            addAssistantMessage("🔧 *[LangChain4J McpClient] Automatically invoking tool 'get_ascii_report'...*");
            String reqJson = String.format("{\"jsonrpc\":\"2.0\",\"id\":\"view_4\",\"method\":\"tools/call\",\"params\":{\"name\":\"get_ascii_report\",\"arguments\":{\"jobId\":\"%s\"}}}",
                    currentJobId);
            executeAndDisplayMcp(reqJson);
        } else {
            // Default response
            addAssistantMessage("I understand your question: \"" + query + "\". Try asking me to 'search' for something, check the 'status' or show the 'summary'!");
        }
    }

    private void executeAndDisplayMcp(String requestJson) {
        try {
            String responseStr = mcpDispatcher.dispatch(requestJson);
            var root = mapper.readTree(responseStr);
            if (root.has("result") && root.get("result").has("content")) {
                var contentArray = root.get("result").get("content");
                if (contentArray.isArray() && contentArray.size() > 0) {
                    String resultText = contentArray.get(0).get("text").asText();
                    addAssistantMessage(resultText);
                }
            } else if (root.has("error")) {
                addAssistantMessage("❌ Error: " + root.get("error").get("message").asText());
            }
        } catch (Exception e) {
            addAssistantMessage("❌ Exception parsing MCP response: " + e.getMessage());
        }
    }

    private void triggerLocalAnalysis() {
        addAssistantMessage("🔧 *[LangChain4J McpClient] Triggering tool 'analyse_project'...*");
        String currentPath = System.getProperty("user.dir");
        String reqJson = String.format("{\"jsonrpc\":\"2.0\",\"id\":\"view_init\",\"method\":\"tools/call\",\"params\":{\"name\":\"analyse_project\",\"arguments\":{\"projectPath\":\"%s\"}}}",
                currentPath.replace("\\", "/"));

        try {
            String responseStr = mcpDispatcher.dispatch(reqJson);
            var root = mapper.readTree(responseStr);
            if (root.has("result") && root.get("result").has("content")) {
                String resultText = root.get("result").get("content").get(0).get("text").asText();
                addAssistantMessage(resultText);

                // Try to extract UUID
                int idIdx = resultText.indexOf("Job ID: ");
                if (idIdx != -1) {
                    String uuidStr = resultText.substring(idIdx + 8, idIdx + 8 + 36);
                    currentJobId = UUID.fromString(uuidStr);
                }
            }
        } catch (Exception e) {
            addAssistantMessage("Error triggering analysis: " + e.getMessage());
        }
    }

    private void askForSummary() {
        if (currentJobId == null) {
            addAssistantMessage("Please run an analysis first!");
            return;
        }
        sendMessageDirectly("summary");
    }

    private void askForReport() {
        if (currentJobId == null) {
            addAssistantMessage("Please run an analysis first!");
            return;
        }
        sendMessageDirectly("report");
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
}
