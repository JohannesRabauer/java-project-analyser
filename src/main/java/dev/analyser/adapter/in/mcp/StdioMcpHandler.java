package dev.analyser.adapter.in.mcp;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class StdioMcpHandler {

    private final McpDispatcher dispatcher;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @ConfigProperty(name = "mcp.stdio.enabled", defaultValue = "true")
    boolean stdioEnabled;

    public StdioMcpHandler(McpDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    void onStart(@Observes StartupEvent event) {
        if (!stdioEnabled) {
            return;
        }

        // Run the Stdio JSON-RPC loop in a dedicated thread
        executor.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {

                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }

                    // Process request and write back the response
                    String responseJson = dispatcher.dispatch(line);
                    writer.println(responseJson);
                    writer.flush();
                }
            } catch (Exception e) {
                System.err.println("Error in StdioMcpHandler loop: " + e.getMessage());
            }
        });
    }
}
