/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.a2aproject.sdk.common.A2AHeaders;
import org.a2aproject.sdk.spec.A2AMethods;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end coverage replacing the Arquillian {@code JakartaA2AServerTest} suites.
 *
 * <p>Requests go over real HTTP through the full servlet filter chain, so the migrated
 * {@code OncePerRequestFilter} routing layer is exercised exactly as a client would exercise it.
 */
@SpringBootTest(classes = TestAgentApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class A2AEndToEndTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    void agentCardIsServedFromWellKnownPath() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/.well-known/agent-card.json", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Test Agent"),
                "agent card did not contain the agent name: " + response.getBody());
    }

    @Test
    void unsupportedProtocolVersionIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(A2AHeaders.A2A_VERSION, "99.0");
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"message/send\",\"params\":{}}";

        ResponseEntity<String> response =
                restTemplate.exchange("/", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("-32009"),
                "expected JSON-RPC error code -32009, got: " + response.getBody());
        assertTrue(response.getBody().contains("Protocol version '99.0' is not supported"),
                "expected unsupported-version message, got: " + response.getBody());
    }

    /**
     * Drives a raw socket rather than {@link TestRestTemplate} because the HTTP client sanitises a
     * non-ASCII header value to {@code ?} before it ever leaves the JVM, which hides the defect under test.
     */
    @Test
    void unsupportedProtocolVersionErrorIsUtf8WithBareJsonContentType() throws Exception {
        byte[] body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"message/send\",\"params\":{}}"
                .getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        request.write(("POST / HTTP/1.1\r\nHost: localhost:" + port + "\r\n"
                + "Content-Type: application/json\r\n").getBytes(StandardCharsets.US_ASCII));
        request.write("A2A-Version: 1.".getBytes(StandardCharsets.US_ASCII));
        request.write(0xE9);
        request.write(("\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        request.write(body);

        byte[] raw;
        try (Socket socket = new Socket("localhost", port)) {
            socket.getOutputStream().write(request.toByteArray());
            socket.getOutputStream().flush();
            raw = socket.getInputStream().readAllBytes();
        }

        int split = indexOf(raw, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        assertTrue(split > 0, "no header/body boundary in response: " + hex(raw));
        String head = new String(raw, 0, split, StandardCharsets.ISO_8859_1);
        byte[] responseBody = java.util.Arrays.copyOfRange(raw, split + 4, raw.length);

        assertTrue(head.startsWith("HTTP/1.1 400"), head);
        assertTrue(head.contains("Content-Type: application/json\r\n"),
                "the source server emits a bare application/json with no charset parameter, got:\n" + head);
        assertTrue(indexOf(responseBody, new byte[] {(byte) 0xC3, (byte) 0xA9}) >= 0,
                "the echoed version must reach the wire as UTF-8 (C3 A9); a bare E9 means the container "
                        + "encoded the body with ISO-8859-1 and it is no longer well-formed JSON. body="
                        + hex(responseBody));
        assertEquals(-1, indexOf(responseBody, new byte[] {(byte) 0xE9, (byte) '\''}),
                "found a raw E9 byte: the error body was written with the container default encoding. body="
                        + hex(responseBody));
    }

    private static String hex(byte[] bytes) {
        if (bytes == null) {
            return "<null>";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; haystack != null && i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    @Test
    void jsonRpcMessageSendRoundTrip() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(A2AHeaders.A2A_VERSION, "1.0");
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + A2AMethods.SEND_MESSAGE_METHOD + "\","
                + "\"params\":{\"message\":{\"messageId\":\"msg-1\",\"role\":\"ROLE_USER\","
                + "\"parts\":[{\"text\":\"World\"}]}}}";

        ResponseEntity<String> response =
                restTemplate.exchange("/", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"id\":1"),
                "response did not echo the JSON-RPC id: " + response.getBody());
        assertFalse(response.getBody().contains("\"error\""),
                "expected a JSON-RPC result, got an error: " + response.getBody());
        assertTrue(response.getBody().contains("Hello World"),
                "the request did not reach the AgentExecutor: " + response.getBody());
    }

    @Test
    void restTransportIsRoutedByVersionHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(A2AHeaders.A2A_VERSION, "1.0");

        ResponseEntity<String> response = restTemplate.exchange(
                "/tasks", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "A2ARestVersionRoutingFilter should rewrite /tasks to the versioned REST transport");
        assertNotNull(response.getBody());
    }
}
