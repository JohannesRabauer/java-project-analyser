package dev.analyser.adapter.in.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@Path("/mcp")
public class SseMcpController {

    private final McpDispatcher dispatcher;
    private final Map<UUID, SseEventSink> sessions = new ConcurrentHashMap<>();

    public SseMcpController(McpDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @GET
    @Path("/sse")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void connect(@Context SseEventSink eventSink, @Context Sse sse) {
        UUID sessionId = UUID.randomUUID();
        sessions.put(sessionId, eventSink);

        // Send initial "endpoint" event with the path for sending messages
        String endpointUrl = "/mcp/message?sessionId=" + sessionId;
        OutboundSseEvent event = sse.newEventBuilder()
                .name("endpoint")
                .data(endpointUrl)
                .build();
        eventSink.send(event);

        // Keep-alive or heartbeat could be run here if needed, or simply trust client connections.
    }

    @POST
    @Path("/message")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response receiveMessage(
            @QueryParam("sessionId") String sessionIdStr,
            String payload,
            @Context Sse sse) {

        if (sessionIdStr == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Missing sessionId parameter")
                    .build();
        }

        UUID sessionId;
        try {
            sessionId = UUID.fromString(sessionIdStr);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid sessionId format")
                    .build();
        }

        SseEventSink eventSink = sessions.get(sessionId);
        if (eventSink == null || eventSink.isClosed()) {
            sessions.remove(sessionId);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Session not found or closed")
                    .build();
        }

        // Run in background / asynchronous thread to not block HTTP thread during heavy computation,
        // and send the result back through the SSE sink when ready!
        try {
            String responseJson = dispatcher.dispatch(payload);

            OutboundSseEvent messageEvent = sse.newEventBuilder()
                    .name("message")
                    .data(responseJson)
                    .build();
            eventSink.send(messageEvent);

            return Response.accepted().build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity("Error processing message: " + e.getMessage())
                    .build();
        }
    }
}
