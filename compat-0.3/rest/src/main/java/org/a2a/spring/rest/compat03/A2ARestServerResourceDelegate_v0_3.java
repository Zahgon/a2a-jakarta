package org.a2a.spring.rest.compat03;

import static org.a2aproject.sdk.compat03.transport.rest.context.RestContextKeys_v0_3.HEADERS_KEY;
import static org.a2aproject.sdk.compat03.transport.rest.context.RestContextKeys_v0_3.METHOD_NAME_KEY;
import static org.a2aproject.sdk.server.ServerCallContext.TRANSPORT_KEY;
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
import org.a2aproject.sdk.compat03.common.A2AHeaders_v0_3;
import org.a2aproject.sdk.compat03.conversion.A2AProtocol_v0_3;
import org.a2aproject.sdk.compat03.spec.CancelTaskRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.DeleteTaskPushNotificationConfigRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.GetAuthenticatedExtendedCardRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.GetTaskPushNotificationConfigRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.GetTaskRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.InternalError_v0_3;
import org.a2aproject.sdk.compat03.spec.InvalidParamsError_v0_3;
import org.a2aproject.sdk.compat03.spec.JSONRPCError_v0_3;
import org.a2aproject.sdk.compat03.spec.ListTaskPushNotificationConfigRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.SendMessageRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.SendStreamingMessageRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.SetTaskPushNotificationConfigRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.TaskResubscriptionRequest_v0_3;
import org.a2aproject.sdk.compat03.transport.rest.handler.RestHandler_v0_3;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.server.extensions.A2AExtensions;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2a.spring.common.SSESubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class A2ARestServerResourceDelegate_v0_3 {

    private static final Logger LOGGER = LoggerFactory.getLogger(A2ARestServerResourceDelegate_v0_3.class);

    private final RestHandler_v0_3 restHandler;

    private static volatile Runnable streamingIsSubscribedRunnable;

    public A2ARestServerResourceDelegate_v0_3(RestHandler_v0_3 restHandler) {
        this.restHandler = restHandler;
    }

    @SuppressWarnings("ReturnValueIgnored")
    public ResponseEntity<String> sendMessage(String body, HttpServletRequest httpRequest, Principal principal) {
        ServerCallContext context = createCallContext(httpRequest, principal, SendMessageRequest_v0_3.METHOD);
        RestHandler_v0_3.HTTPRestResponse response = null;
        try {
            response = restHandler.sendMessage(body, context);
        } catch (JSONRPCError_v0_3 e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new InternalError_v0_3(t.getMessage()));
        } finally {
            return ResponseEntity.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .body(response.getBody());
        }
    }

    public void sendMessageStreaming(String body, HttpServletRequest httpRequest, HttpServletResponse httpResponse, Principal principal) throws IOException {
        ServerCallContext context = createCallContext(httpRequest, principal, SendStreamingMessageRequest_v0_3.METHOD);
        RestHandler_v0_3.HTTPRestStreamingResponse streamingResponse = null;
        RestHandler_v0_3.HTTPRestResponse error = null;
        try {
            RestHandler_v0_3.HTTPRestResponse response = restHandler.sendStreamingMessage(body, context);
            if (response instanceof RestHandler_v0_3.HTTPRestStreamingResponse hTTPRestStreamingResponse) {
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

    @SuppressWarnings("ReturnValueIgnored")
    public ResponseEntity<String> getTask(String taskId, String historyLengthSnakeStr, String historyLengthCamelStr,
            HttpServletRequest httpRequest, Principal principal) {
        ServerCallContext context = createCallContext(httpRequest, principal, GetTaskRequest_v0_3.METHOD);
        RestHandler_v0_3.HTTPRestResponse response = null;
        try {
            if (taskId == null || taskId.isEmpty()) {
                response = restHandler.createErrorResponse(new InvalidParamsError_v0_3("bad task id"));
            } else {
                boolean hasHistoryLength = historyLengthSnakeStr != null && !historyLengthSnakeStr.isEmpty();
                boolean hasHistoryLengthCamel = historyLengthCamelStr != null && !historyLengthCamelStr.isEmpty();

                if (hasHistoryLength && hasHistoryLengthCamel) {
                    response = restHandler.createErrorResponse(
                        new InvalidParamsError_v0_3("Only one of 'history_length' or 'historyLength' may be specified"));
                } else {
                    int historyLength = 0;
                    if (hasHistoryLength) {
                        historyLength = Integer.parseInt(historyLengthSnakeStr);
                    } else if (hasHistoryLengthCamel) {
                        historyLength = Integer.parseInt(historyLengthCamelStr);
                    }
                    response = restHandler.getTask(taskId, historyLength, context);
                }
            }
        } catch (NumberFormatException e) {
            response = restHandler.createErrorResponse(new InvalidParamsError_v0_3("bad history_length or historyLength"));
        } catch (JSONRPCError_v0_3 e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new InternalError_v0_3(t.getMessage()));
        } finally {
            return ResponseEntity.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .body(response.getBody());
        }
    }

    @SuppressWarnings("ReturnValueIgnored")
    public ResponseEntity<String> cancelTask(String taskId, HttpServletRequest httpRequest, Principal principal) {
        ServerCallContext context = createCallContext(httpRequest, principal, CancelTaskRequest_v0_3.METHOD);
        RestHandler_v0_3.HTTPRestResponse response = null;
        try {
            if (taskId == null || taskId.isEmpty()) {
                response = restHandler.createErrorResponse(new InvalidParamsError_v0_3("bad task id"));
            } else {
                response = restHandler.cancelTask(taskId, context);
            }
        } catch (JSONRPCError_v0_3 e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new InternalError_v0_3(t.getMessage()));
        } finally {
            return ResponseEntity.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .body(response.getBody());
        }
    }

    public void resubscribeTask(String taskId, HttpServletRequest httpRequest, HttpServletResponse httpResponse, Principal principal) throws IOException {
        ServerCallContext context = createCallContext(httpRequest, principal, TaskResubscriptionRequest_v0_3.METHOD);
        RestHandler_v0_3.HTTPRestStreamingResponse streamingResponse = null;
        RestHandler_v0_3.HTTPRestResponse error = null;
        try {
            if (taskId == null || taskId.isEmpty()) {
                error = restHandler.createErrorResponse(new InvalidParamsError_v0_3("bad task id"));
            } else {
                RestHandler_v0_3.HTTPRestResponse response = restHandler.resubscribeTask(taskId, context);
                if (response instanceof RestHandler_v0_3.HTTPRestStreamingResponse hTTPRestStreamingResponse) {
                    streamingResponse = hTTPRestStreamingResponse;
                } else {
                    error = response;
                }
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
        RestHandler_v0_3.HTTPRestResponse response = restHandler.getAgentCard();

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
        createCallContext(httpRequest, principal, GetAuthenticatedExtendedCardRequest_v0_3.METHOD);
        RestHandler_v0_3.HTTPRestResponse response = restHandler.getAuthenticatedExtendedCard();
        return ResponseEntity.status(response.getStatusCode())
                .header(CONTENT_TYPE, response.getContentType())
                .body(response.getBody());
    }

    @SuppressWarnings("ReturnValueIgnored")
    public ResponseEntity<String> setTaskPushNotificationConfiguration(String taskId, String body, HttpServletRequest httpRequest, Principal principal) {
        ServerCallContext context = createCallContext(httpRequest, principal, SetTaskPushNotificationConfigRequest_v0_3.METHOD);
        RestHandler_v0_3.HTTPRestResponse response = null;
        try {
            if (taskId == null || taskId.isEmpty()) {
                response = restHandler.createErrorResponse(new InvalidParamsError_v0_3("bad task id"));
            } else {
                response = restHandler.setTaskPushNotificationConfiguration(taskId, body, context);
            }
        } catch (JSONRPCError_v0_3 e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new InternalError_v0_3(t.getMessage()));
        } finally {
            return ResponseEntity.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .body(response.getBody());
        }
    }

    @SuppressWarnings("ReturnValueIgnored")
    public ResponseEntity<String> getTaskPushNotificationConfiguration(String taskId, String configId, HttpServletRequest httpRequest, Principal principal) {
        ServerCallContext context = createCallContext(httpRequest, principal, GetTaskPushNotificationConfigRequest_v0_3.METHOD);
        RestHandler_v0_3.HTTPRestResponse response = null;
        try {
            if (taskId == null || taskId.isEmpty()) {
                response = restHandler.createErrorResponse(new InvalidParamsError_v0_3("bad task id"));
            } else {
                response = restHandler.getTaskPushNotificationConfiguration(taskId, configId, context);
            }
        } catch (JSONRPCError_v0_3 e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new InternalError_v0_3(t.getMessage()));
        } finally {
            return ResponseEntity.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .body(response.getBody());
        }
    }

    @SuppressWarnings("ReturnValueIgnored")
    public ResponseEntity<String> listTaskPushNotificationConfigurations(String taskId, HttpServletRequest httpRequest, Principal principal) {
        ServerCallContext context = createCallContext(httpRequest, principal, ListTaskPushNotificationConfigRequest_v0_3.METHOD);
        RestHandler_v0_3.HTTPRestResponse response = null;
        try {
            if (taskId == null || taskId.isEmpty()) {
                response = restHandler.createErrorResponse(new InvalidParamsError_v0_3("bad task id"));
            } else {
                response = restHandler.listTaskPushNotificationConfigurations(taskId, context);
            }
        } catch (JSONRPCError_v0_3 e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new InternalError_v0_3(t.getMessage()));
        } finally {
            return ResponseEntity.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .body(response.getBody());
        }
    }

    @SuppressWarnings("ReturnValueIgnored")
    public ResponseEntity<String> deleteTaskPushNotificationConfiguration(String taskId, String configId, HttpServletRequest httpRequest, Principal principal) {
        ServerCallContext context = createCallContext(httpRequest, principal, DeleteTaskPushNotificationConfigRequest_v0_3.METHOD);
        RestHandler_v0_3.HTTPRestResponse response = null;
        try {
            if (taskId == null || taskId.isEmpty()) {
                response = restHandler.createErrorResponse(new InvalidParamsError_v0_3("bad task id"));
            } else if (configId == null || configId.isEmpty()) {
                response = restHandler.createErrorResponse(new InvalidParamsError_v0_3("bad config id"));
            } else {
                response = restHandler.deleteTaskPushNotificationConfiguration(taskId, configId, context);
            }
        } catch (JSONRPCError_v0_3 e) {
            response = restHandler.createErrorResponse(e);
        } catch (Throwable t) {
            response = restHandler.createErrorResponse(new InternalError_v0_3(t.getMessage()));
        } finally {
            return ResponseEntity.status(response.getStatusCode())
                    .header(CONTENT_TYPE, response.getContentType())
                    .body(response.getBody());
        }
    }

    private void sendErrorResponse(HttpServletResponse httpResponse, RestHandler_v0_3.HTTPRestResponse error) throws IOException {
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
        A2ARestServerResourceDelegate_v0_3.streamingIsSubscribedRunnable = streamingIsSubscribedRunnable;
        SSESubscriber.setStreamingIsSubscribedRunnable(streamingIsSubscribedRunnable);
    }

    protected ServerCallContext createCallContext(HttpServletRequest request, Principal principal, String jsonRpcMethodName) {
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
        state.put(METHOD_NAME_KEY, jsonRpcMethodName);
        state.put(TRANSPORT_KEY, TransportProtocol.HTTP_JSON);

        Enumeration<String> en = request.getHeaders(A2AHeaders_v0_3.X_A2A_EXTENSIONS);
        List<String> extensionHeaderValues = new ArrayList<>();
        while (en.hasMoreElements()) {
            extensionHeaderValues.add(en.nextElement());
        }
        Set<String> requestedExtensions = A2AExtensions.getRequestedExtensions(extensionHeaderValues);

        return new ServerCallContext(user, state, requestedExtensions, A2AProtocol_v0_3.PROTOCOL_VERSION);
    }
}
