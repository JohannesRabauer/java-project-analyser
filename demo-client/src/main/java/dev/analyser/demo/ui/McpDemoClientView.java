package dev.analyser.demo.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import dev.analyser.demo.client.McpHttpClient;
import java.util.UUID;

@Route("")
public class McpDemoClientView extends VerticalLayout {

    private final McpHttpClient mcpHttpClient;
    private final ObjectMapper objectMapper;
    private final VerticalLayout chatArea = new VerticalLayout();
    private final TextField serverBaseUrl = new TextField("MCP Server URL");
    private final TextField gitUrl = new TextField("Git Repository URL");
    private final TextField projectPath = new TextField("Local Project Path (alternative)");
    private final TextField resourceUriField = new TextField("Resource URI");
    private final TextField queryField = new TextField();
    private UUID currentJobId;

    public McpDemoClientView(McpHttpClient mcpHttpClient, ObjectMapper objectMapper) {
        this.mcpHttpClient = mcpHttpClient;
        this.objectMapper = objectMapper;

        setSizeFull();
        setSpacing(false);
        setPadding(false);
        getStyle().set("background-color", "#0f172a");
        getStyle().set("color", "#f8fafc");
        getStyle().set("font-family", "'Outfit', 'Inter', sans-serif");

        HorizontalLayout mainLayout = new HorizontalLayout();
        mainLayout.setSizeFull();
        mainLayout.setSpacing(false);

        mainLayout.add(createSidebar());
        mainLayout.add(createChatContainer());
        add(mainLayout);
    }

    private VerticalLayout createSidebar() {
        VerticalLayout layout = new VerticalLayout();
        layout.setWidth("360px");
        layout.setHeightFull();
        layout.getStyle().set("background-color", "#0b0f19");
        layout.getStyle().set("border-right", "1px solid #1e293b");
        layout.getStyle().set("padding", "24px");

        H3 title = new H3("Standalone Demo Client");
        title.getStyle().set("color", "#38bdf8");
        title.getStyle().set("margin-top", "0");

        serverBaseUrl.setValue("http://localhost:8080");
        serverBaseUrl.setWidthFull();
        gitUrl.setPlaceholder("https://github.com/owner/repo");
        gitUrl.setWidthFull();
        projectPath.setPlaceholder("/absolute/path/to/project");
        projectPath.setWidthFull();
        resourceUriField.setValue("mcp://analysis/jobs");
        resourceUriField.setWidthFull();

        Button listTools = sidebarButton("List MCP Tools", VaadinIcon.TOOLS, this::listTools);
        Button listResources = sidebarButton("List Resources", VaadinIcon.DATABASE, this::listResources);
        Button listPrompts = sidebarButton("List Prompts", VaadinIcon.COMMENT_O, this::listPrompts);
        Button analyseProject = sidebarButton("Analyse Project", VaadinIcon.PLAY, this::analyseProject);
        Button listJobs = sidebarButton("Read Jobs Resource", VaadinIcon.LIST, this::showJobsResource);
        Button showStatus = sidebarButton("Show Status", VaadinIcon.TIMER, this::showStatus);
        Button showSummary = sidebarButton("Show Summary", VaadinIcon.INFO_CIRCLE, this::showSummary);
        Button showStructure = sidebarButton("Show Structure Resource", VaadinIcon.SITEMAP, this::showStructureResource);
        Button showReport = sidebarButton("Show Ascii Report", VaadinIcon.FILE_TEXT, this::showReport);
        Button showRiskPrompt = sidebarButton("Show Risk Prompt", VaadinIcon.WARNING, () -> showPrompt("review-codebase-risks"));
        Button showFeaturePrompt = sidebarButton("Show Feature Prompt", VaadinIcon.LIGHTBULB, () -> showPrompt("suggest-feature-impl"));
        Button readResource = sidebarButton("Read Resource URI", VaadinIcon.LINK, this::readCustomResource);

        layout.add(
                title,
                serverBaseUrl,
                gitUrl,
                projectPath,
                resourceUriField,
                listTools,
                listResources,
                listPrompts,
                analyseProject,
                listJobs,
                showStatus,
                showSummary,
                showStructure,
                showReport,
                showRiskPrompt,
                showFeaturePrompt,
                readResource);
        return layout;
    }

    private VerticalLayout createChatContainer() {
        VerticalLayout chatContainer = new VerticalLayout();
        chatContainer.setSizeFull();
        chatContainer.setPadding(true);
        chatContainer.setSpacing(true);
        chatContainer.getStyle().set("background", "radial-gradient(circle at top right, #1e293b, #0f172a)");

        H1 title = new H1("MCP Demo Client");
        title.getStyle().set("font-size", "1.8rem");
        title.getStyle().set("font-weight", "800");
        title.getStyle().set("background", "linear-gradient(to right, #38bdf8, #818cf8)");
        title.getStyle().set("-webkit-background-clip", "text");
        title.getStyle().set("-webkit-text-fill-color", "transparent");
        title.getStyle().set("margin", "0");

        chatArea.setSizeFull();
        chatArea.getStyle().set("overflow-y", "auto");
        chatArea.getStyle().set("padding", "10px");
        addAssistantMessage("This demo client is a separate application. It talks to the MCP server over HTTP/SSE instead of sharing the same Quarkus process.");

        queryField.setPlaceholder("Search the analysed codebase...");
        queryField.setWidthFull();
        Button sendQuery = new Button(new Icon(VaadinIcon.SEARCH), event -> searchCodebase());
        HorizontalLayout inputLayout = new HorizontalLayout(queryField, sendQuery);
        inputLayout.setWidthFull();
        inputLayout.expand(queryField);

        chatContainer.add(title, chatArea, inputLayout);
        chatContainer.expand(chatArea);
        return chatContainer;
    }

    private Button sidebarButton(String label, VaadinIcon icon, Runnable action) {
        Button button = new Button(label, new Icon(icon), event -> action.run());
        button.setWidthFull();
        button.getStyle().set("background-color", "#1e293b");
        button.getStyle().set("color", "#f8fafc");
        button.getStyle().set("border", "1px solid #334155");
        button.getStyle().set("border-radius", "8px");
        return button;
    }

    private void listTools() {
        try {
            JsonNode response = mcpHttpClient.listTools(serverBaseUrl.getValue());
            addAssistantMessage(response.toPrettyString());
        } catch (RuntimeException exception) {
            addAssistantMessage("Failed to list tools: " + exception.getMessage());
        }
    }

    private void listResources() {
        try {
            JsonNode response = mcpHttpClient.listResources(serverBaseUrl.getValue());
            addAssistantMessage(response.toPrettyString());
        } catch (RuntimeException exception) {
            addAssistantMessage("Failed to list resources: " + exception.getMessage());
        }
    }

    private void listPrompts() {
        try {
            JsonNode response = mcpHttpClient.listPrompts(serverBaseUrl.getValue());
            addAssistantMessage(response.toPrettyString());
        } catch (RuntimeException exception) {
            addAssistantMessage("Failed to list prompts: " + exception.getMessage());
        }
    }

    private void analyseProject() {
        String gitUrlValue = gitUrl.getValue().trim();
        String projectPathValue = projectPath.getValue().trim();

        if (gitUrlValue.isEmpty() && projectPathValue.isEmpty()) {
            addAssistantMessage("Enter either a Git repository URL or an absolute local project path.");
            return;
        }

        ObjectNode arguments = json();
        if (!gitUrlValue.isEmpty()) {
            arguments.put("gitUrl", gitUrlValue);
        }
        if (!projectPathValue.isEmpty()) {
            arguments.put("projectPath", projectPathValue);
        }
        invokeTool("analyse_project", arguments, true);
    }

    private void showStatus() {
        if (!ensureJobSelected()) {
            return;
        }
        invokeTool("get_analysis_status", json().put("jobId", currentJobId.toString()), false);
    }

    private void showSummary() {
        if (!ensureJobSelected()) {
            return;
        }
        invokeTool("get_project_summary", json().put("jobId", currentJobId.toString()), false);
    }

    private void showReport() {
        if (!ensureJobSelected()) {
            return;
        }
        invokeTool("get_ascii_report", json().put("jobId", currentJobId.toString()), false);
    }

    private void showJobsResource() {
        showResource("mcp://analysis/jobs", true);
    }

    private void showStructureResource() {
        if (!ensureJobSelected()) {
            return;
        }
        showResource(jobResourceUri("structure"), false);
    }

    private void readCustomResource() {
        String resourceUri = resourceUriField.getValue().trim();
        if (resourceUri.isEmpty()) {
            addAssistantMessage("Enter a resource URI first.");
            return;
        }
        showResource(resourceUri, true);
    }

    private void showPrompt(String promptName) {
        try {
            JsonNode response = mcpHttpClient.getPrompt(serverBaseUrl.getValue(), promptName);
            addAssistantMessage(response.toPrettyString());
        } catch (RuntimeException exception) {
            addAssistantMessage("Failed to get prompt: " + exception.getMessage());
        }
    }

    private void searchCodebase() {
        if (!ensureJobSelected()) {
            return;
        }
        String query = queryField.getValue().trim();
        if (query.isEmpty()) {
            addAssistantMessage("Enter a search query first.");
            return;
        }
        addUserMessage(query);
        invokeTool("search_codebase", json().put("jobId", currentJobId.toString()).put("query", query), false);
        queryField.clear();
    }

    private void invokeTool(String toolName, ObjectNode arguments, boolean trackJobId) {
        try {
            String response = mcpHttpClient.callTool(serverBaseUrl.getValue(), toolName, arguments);
            addAssistantMessage(response);
            if (trackJobId) {
                captureJobId(response);
            }
        } catch (RuntimeException exception) {
            addAssistantMessage("Tool call failed: " + exception.getMessage());
        }
    }

    private boolean ensureJobSelected() {
        if (currentJobId != null) {
            return true;
        }
        addAssistantMessage("Start an analysis first so the demo client has a job ID to work with.");
        return false;
    }

    private void captureJobId(String response) {
        int start = response.indexOf("Job ID: ");
        if (start == -1) {
            return;
        }
        currentJobId = UUID.fromString(response.substring(start + 8, start + 44));
        resourceUriField.setValue(jobResourceUri("summary"));
    }

    private void showResource(String resourceUri, boolean allowJobSelectionFromResponse) {
        try {
            JsonNode response = mcpHttpClient.readResource(serverBaseUrl.getValue(), resourceUri);
            addAssistantMessage(response.toPrettyString());
            resourceUriField.setValue(resourceUri);
            if (allowJobSelectionFromResponse) {
                captureJobIdFromJobsResource(response);
            }
        } catch (RuntimeException exception) {
            addAssistantMessage("Failed to read resource: " + exception.getMessage());
        }
    }

    private void captureJobIdFromJobsResource(JsonNode response) {
        JsonNode resources = response.path("result").path("contents");
        if (!resources.isArray() || resources.isEmpty()) {
            return;
        }

        JsonNode jobsText = resources.get(0).path("text");
        if (!jobsText.isTextual()) {
            return;
        }

        try {
            JsonNode jobs = objectMapper.readTree(jobsText.asText());
            if (!jobs.isArray() || jobs.isEmpty()) {
                return;
            }

            JsonNode firstJobId = jobs.get(0).path("jobId");
            if (!firstJobId.isTextual()) {
                return;
            }

            currentJobId = UUID.fromString(firstJobId.asText());
            resourceUriField.setValue(jobResourceUri("summary"));
            addAssistantMessage("Selected job from jobs resource: " + currentJobId);
        } catch (Exception exception) {
            addAssistantMessage("Could not auto-select a job from the jobs resource: " + exception.getMessage());
        }
    }

    private String jobResourceUri(String resourceType) {
        return "mcp://analysis/job/" + currentJobId + "/" + resourceType;
    }

    private ObjectNode json() {
        return objectMapper.createObjectNode();
    }

    private void addUserMessage(String text) {
        Div bubble = new Div(new Text(text));
        bubble.getStyle().set("background-color", "#4f46e5");
        bubble.getStyle().set("color", "#ffffff");
        bubble.getStyle().set("padding", "10px 16px");
        bubble.getStyle().set("border-radius", "16px 16px 4px 16px");
        bubble.getStyle().set("align-self", "flex-end");
        bubble.getStyle().set("max-width", "70%");
        chatArea.add(bubble);
    }

    private void addAssistantMessage(String text) {
        Div bubble = new Div(new Text(text));
        bubble.getStyle().set("white-space", "pre-wrap");
        bubble.getStyle().set("background-color", "#1e293b");
        bubble.getStyle().set("color", "#f1f5f9");
        bubble.getStyle().set("padding", "12px 18px");
        bubble.getStyle().set("border-radius", "16px 16px 16px 4px");
        bubble.getStyle().set("align-self", "flex-start");
        bubble.getStyle().set("max-width", "85%");
        bubble.getStyle().set("border", "1px solid #334155");
        chatArea.add(bubble);
    }
}
