package dev.analyser.adapter.in.mcp;

import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * MCP stdio transport handler. Disabled by default (HTTP/SSE is the default mode).
 * Enable via mcp.stdio.enabled=true or MCP_STDIO_ENABLED=true env var.
 */
@ApplicationScoped
public class StdioMcpHandler {

    private final McpDispatcher dispatcher;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @ConfigProperty(name = "mcp.stdio.enabled", defaultValue = "false")
    boolean stdioEnabled;

    public StdioMcpHandler(McpDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    void onStart(@Observes StartupEvent event) {
        if (!stdioEnabled) {
            return;
        }

        executor.submit(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                 var writer = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {

                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    String responseJson = dispatcher.dispatch(line);
                    writer.println(responseJson);
                    writer.flush();
                }
            } catch (Exception e) {
                // Stdio transport closed — normal shutdown
            }
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
