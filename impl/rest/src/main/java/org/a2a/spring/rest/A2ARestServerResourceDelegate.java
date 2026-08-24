package org.a2a.spring.rest;

import static org.a2aproject.sdk.server.ServerCallContext.TRANSPORT_KEY;
import static org.a2aproject.sdk.transport.rest.context.RestContextKeys.HEADERS_KEY;
import static org.a2aproject.sdk.transport.rest.context.RestContextKeys.TENANT_KEY;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.a2aproject.sdk.common.A2AHeaders;
import org.a2a.spring.common.PathRewriteHttpServletRequest;
import org.a2a.spring.common.SSESubscriber;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.server.extensions.A2AExtensions;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class A2ARestServerResourceDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(A2ARestServerResourceDelegate.class);
    private static final String PAGE_SIZE_PARAM = "pageSize";
    private static final String PAGE_TOKEN_PARAM = "pageToken";
    private static final String HISTORY_LENGTH_PARAM = "historyLength";
    private static final String STATUS_TIMESTAMP_AFTER = "statusTimestampAfter";

    private final RestHandler restHandler;

    private static volatile Runnable streamingIsSubscribedRunnable;

    public A2ARestServerResourceDelegate(RestHandler restHandler) {
        this.restHandler = restHandler;
    }

    @SuppressWarnings("ReturnValueIgnored")
    public ResponseEntity<String> sendMessage(String body, HttpServletRequest httpRequest, Principal principal) {
        ServerCallContext context = createCallContext(httpRequest, principal);
        RestHandler.HTTPRestResponse response = null;
        try {
            response = restHandler.sendMessage(context, extractTenant(httpRequest), body);
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        } finally {
            return ResponseEntity.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .body(response.getBody());
        }
    }

    public void sendMessageStreaming(String body, HttpServletRequest httpRequest, HttpServletResponse httpResponse, Principal principal) throws IOException {
        ServerCallContext context = createCallContext(httpRequest, principal);
        RestHandler.HTTPRestStreamingResponse streamingResponse = null;
        RestHandler.HTTPRestResponse error = null;
        try {
            RestHandler.HTTPRestResponse response = restHandler.sendStreamingMessage(context, extractTenant(httpRequest), body);
            if (response instanceof RestHandler.HTTPRestStreamingResponse hTTPRestStreamingResponse) {
                streamingResponse = hTTPRestStreamingResponse;
            } else {
                error = response;
            }
        } finally {
            if (error != null) {
                sendErrorResponse(httpResponse, error);
            } else {
                handleCustomSSEResponse(streamingResponse.getPublisher(), httpResponse, context);
            }
        }
    }

    public void resubscribeTask(String taskId, HttpServletRequest httpRequest, HttpServletResponse httpResponse, Principal principal) throws IOException {
        ServerCallContext context = createCallContext(httpRequest, principal);
        RestHandler.HTTPRestStreamingResponse streamingResponse = null;
        RestHandler.HTTPRestResponse error = null;
        try {
            RestHandler.HTTPRestResponse response = restHandler.subscribeToTask(context, extractTenant(httpRequest), taskId);
            if (response instanceof RestHandler.HTTPRestStreamingResponse hTTPRestStreamingResponse) {
                streamingResponse = hTTPRestStreamingResponse;
            } else {
                error = response;
            }
        } finally {
            if (error != null) {
                sendErrorResponse(httpResponse, error);
            } else {
                handleCustomSSEResponse(streamingResponse.getPublisher(), httpResponse, context);
            }
        }
    }

    public ResponseEntity<String> getAgentCard() {
        RestHandler.HTTPRestResponse response = restHandler.getAgentCard();

        String etag = "\"" + Integer.toHexString(response.getBody().hashCode()) + "\"";

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("GMT"));
        String lastModified = now.format(DateTimeFormatter.RFC_1123_DATE_TIME);

        return ResponseEntity.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .header("Cache-Control", "max-age=3600")
                .header("ETag", etag)
                .header("Last-Modified", lastModified)
                .body(response.getBody());
    }

    public ResponseEntity<String> getAuthenticatedExtendedCard(HttpServletRequest httpRequest, Principal principal) {
        ServerCallContext context = createCallContext(httpRequest, principal);
        RestHandler.HTTPRestResponse response = restHandler.getExtendedAgentCard(context, extractTenant(httpRequest));
        return ResponseEntity.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .body(response.getBody());
    }

    public ResponseEntity<String> getExtendedAgentCard(HttpServletRequest httpRequest, Principal principal) {
        ServerCallContext context = createCallContext(httpRequest, principal);
        RestHandler.HTTPRestResponse response = restHandler.getExtendedAgentCard(context, extractTenant(httpRequest));
        return ResponseEntity.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .body(response.getBody());
    }

    @SuppressWarnings("ReturnValueIgnored")
    public ResponseEntity<String> listTasks(HttpServletRequest httpRequest, Principal principal) {
        ServerCallContext context = createCallContext(httpRequest, principal);
        RestHandler.HTTPRestResponse response = null;
        try {
            String contextId = httpRequest.getParameter("contextId");
            String statusStr = httpRequest.getParameter("status");
            if (statusStr != null && !statusStr.isEmpty()) {
                statusStr = statusStr.toUpperCase();
            }
            String pageSizeStr = httpRequest.getParameter(PAGE_SIZE_PARAM);
            String pageToken = httpRequest.getParameter(PAGE_TOKEN_PARAM);
            String historyLengthStr = httpRequest.getParameter(HISTORY_LENGTH_PARAM);
            String statusTimestampAfter = httpRequest.getParameter(STATUS_TIMESTAMP_AFTER);
            String includeArtifactsStr = httpRequest.getParameter("includeArtifacts");

            Integer pageSize = null;
            if (pageSizeStr != null && !pageSizeStr.isEmpty()) {
                pageSize = Integer.valueOf(pageSizeStr);
            }

            Integer historyLength = null;
            if (historyLengthStr != null && !historyLengthStr.isEmpty()) {
                historyLength = Integer.valueOf(historyLengthStr);
            }

            Boolean includeArtifacts = null;
            if (includeArtifactsStr != null && !includeArtifactsStr.isEmpty()) {
                includeArtifacts = Boolean.valueOf(includeArtifactsStr);
            }

            response = restHandler.listTasks(context, extractTenant(httpRequest), contextId, statusStr, pageSize,
                    pageToken, historyLength, statusTimestampAfter, includeArtifacts);
        } catch (NumberFormatException e) {
            response = restHandler.createErrorResponse(new InvalidParamsError("Invalid number format in parameters"));
        } catch (IllegalArgumentException e) {
            response = restHandler.createErrorResponse(new InvalidParamsError("Invalid parameter value: " + e.getMessage()));
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        } finally {
            return ResponseEntity.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .body(response.getBody());
        }
    }

    @SuppressWarnings("ReturnValueIgnored")
    public ResponseEntity<String> getTask(String taskId, String historyLengthStr, HttpServletRequest httpRequest, Principal principal) {
        ServerCallContext context = createCallContext(httpRequest, principal);
        RestHandler.HTTPRestResponse response = null;
        try {
            Integer historyLength = null;
            if (historyLengthStr != null && !historyLengthStr.isEmpty()) {
                historyLength = Integer.valueOf(historyLengthStr);
            }
            response = restHandler.getTask(context, extractTenant(httpRequest), taskId, historyLength);
        } catch (NumberFormatException e) {
            response = restHandler.createErrorResponse(new InvalidParamsError("bad historyLength"));
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        } finally {
            return ResponseEntity.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .body(response.getBody());
        }
    }

    @SuppressWarnings("ReturnValueIgnored")
    public ResponseEntity<String> cancelTask(String taskId, String body, HttpServletRequest httpRequest, Principal principal) {
        ServerCallContext context = createCallContext(httpRequest, principal);
        RestHandler.HTTPRestResponse response = null;
        try {
            response = restHandler.cancelTask(context, extractTenant(httpRequest), body, taskId);
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        } finally {
            return ResponseEntity.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .body(response.getBody());
        }
    }

    @SuppressWarnings("ReturnValueIgnored")
    public ResponseEntity<String> setTaskPushNotificationConfiguration(String taskId, String body, HttpServletRequest httpRequest, Principal principal) {
        ServerCallContext context = createCallContext(httpRequest, principal);
        RestHandler.HTTPRestResponse response = null;
        try {
            response = restHandler.createTaskPushNotificationConfiguration(context, extractTenant(httpRequest), body, taskId);
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        } finally {
            return ResponseEntity.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .body(response.getBody());
        }
    }

    @SuppressWarnings("ReturnValueIgnored")
    public ResponseEntity<String> getTaskPushNotificationConfiguration(String taskId, String configId, HttpServletRequest httpRequest, Principal principal) {
        ServerCallContext context = createCallContext(httpRequest, principal);
        RestHandler.HTTPRestResponse response = null;
        try {
            response = restHandler.getTaskPushNotificationConfiguration(context, extractTenant(httpRequest), taskId, configId);
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        } finally {
            return ResponseEntity.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .body(response.getBody());
        }
    }

    public ResponseEntity<String> getOrListTaskPushNotificationConfigurations(String taskId, HttpServletRequest httpRequest, Principal principal) {
        ServerCallContext context = createCallContext(httpRequest, principal);
        RestHandler.HTTPRestResponse response = null;
        try {
            if (taskId == null || taskId.isEmpty()) {
                response = restHandler.createErrorResponse(new InvalidParamsError("bad task id"));
            } else {
                String requestURI = PathRewriteHttpServletRequest.originalRequestUri(httpRequest);
                if (requestURI.endsWith("/")) {
                    response = restHandler.getTaskPushNotificationConfiguration(context, extractTenant(httpRequest),
                            taskId, null);
                } else {
                    int pageSize = 0;
                    if (httpRequest.getParameter(PAGE_SIZE_PARAM) != null) {
                        pageSize = Integer.parseInt(httpRequest.getParameter(PAGE_SIZE_PARAM));
                    }
                    String pageToken = "";
                    if (httpRequest.getParameter(PAGE_TOKEN_PARAM) != null) {
                        pageToken = httpRequest.getParameter(PAGE_TOKEN_PARAM);
                    }
                    response = restHandler.listTaskPushNotificationConfigurations(context, extractTenant(httpRequest),
                            taskId, pageSize, pageToken);
                }
            }
        } catch (NumberFormatException e) {
            response = restHandler.createErrorResponse(new InvalidParamsError("bad " + PAGE_SIZE_PARAM));
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        }
        return ResponseEntity.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .body(response.getBody());
    }

    @SuppressWarnings("ReturnValueIgnored")
    public ResponseEntity<String> deleteTaskPushNotificationConfiguration(String taskId, String configId, HttpServletRequest httpRequest, Principal principal) {
        ServerCallContext context = createCallContext(httpRequest, principal);
        RestHandler.HTTPRestResponse response = null;
        try {
            response = restHandler.deleteTaskPushNotificationConfiguration(context, extractTenant(httpRequest), taskId, configId);
        } catch (A2AError e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new org.a2aproject.sdk.spec.InternalError(t.getMessage()));
        } finally {
            return ResponseEntity.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .body(response.getBody());
        }
    }

    private void sendErrorResponse(HttpServletResponse httpResponse, RestHandler.HTTPRestResponse error) throws IOException {
        httpResponse.setStatus(error.getStatusCode());
        httpResponse.setHeader(CONTENT_TYPE, error.getContentType());
        httpResponse.getWriter().write(error.getBody());
        httpResponse.getWriter().flush();
    }

    private void handleCustomSSEResponse(Flow.Publisher<String> publisher,
            HttpServletResponse response,
            ServerCallContext context) throws IOException {
        response.setHeader(CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        CompletableFuture<Void> streamingComplete = new CompletableFuture<>();
        try (PrintWriter writer = response.getWriter()) {
            writer.write(": SSE stream started\n\n");
            writer.flush();
            publisher.subscribe(new SSESubscriber(streamingComplete, writer, context));
            streamingComplete.get();
        } catch (Exception e) {
            LOGGER.error("Error waiting for streaming completion: {}", e.getMessage(), e);
            throw new IOException("Streaming failed", e);
        }
    }

    public static void setStreamingIsSubscribedRunnable(Runnable streamingIsSubscribedRunnable) {
        A2ARestServerResourceDelegate.streamingIsSubscribedRunnable = streamingIsSubscribedRunnable;
        SSESubscriber.setStreamingIsSubscribedRunnable(streamingIsSubscribedRunnable);
    }

    protected ServerCallContext createCallContext(HttpServletRequest request, Principal principal) {
        User user;

        if (principal == null) {
            user = UnauthenticatedUser.INSTANCE;
        } else {
            user = new User() {
                @Override
                public boolean isAuthenticated() {
                    return true;
                }

                @Override
                public String getUsername() {
                    return principal.getName();
                }
            };
        }
        Map<String, Object> state = new HashMap<>();

        Map<String, String> headers = new HashMap<>();
        for (Enumeration<String> headerNames = request.getHeaderNames(); headerNames.hasMoreElements();) {
            String name = headerNames.nextElement();
            headers.put(name, request.getHeader(name));
        }

        state.put(HEADERS_KEY, headers);
        state.put(TENANT_KEY, extractTenant(request));
        state.put(TRANSPORT_KEY, TransportProtocol.HTTP_JSON);

        Enumeration<String> en = request.getHeaders(A2AHeaders.A2A_EXTENSIONS);
        List<String> extensionHeaderValues = new ArrayList<>();
        while (en.hasMoreElements()) {
            extensionHeaderValues.add(en.nextElement());
        }
        Set<String> requestedExtensions = A2AExtensions.getRequestedExtensions(extensionHeaderValues);
        String requestedVersion = request.getHeader(A2AHeaders.A2A_VERSION);
        return new ServerCallContext(user, state, requestedExtensions, requestedVersion);
    }

    private String extractTenant(HttpServletRequest request) {
        String requestURI = PathRewriteHttpServletRequest.originalRequestUri(request);
        if (requestURI == null || requestURI.isBlank()) {
            return "";
        }

        if (requestURI.startsWith("/")) {
            requestURI = requestURI.substring(1);
        }

        int slashIndex = requestURI.indexOf('/');
        int colonIndex = requestURI.indexOf(':');
        String firstSegment;

        if (colonIndex >= 0 && (slashIndex < 0 || colonIndex < slashIndex)) {
            firstSegment = requestURI.substring(0, colonIndex);
        } else if (slashIndex > 0) {
            firstSegment = requestURI.substring(0, slashIndex);
        } else {
            firstSegment = requestURI;
        }

        if (firstSegment.equals("message") ||
            firstSegment.equals("tasks") ||
            firstSegment.equals("card") ||
            firstSegment.equals("extendedAgentCard") ||
            firstSegment.equals(".well-known")) {
            return "";
        }

        return firstSegment;
    }
}
