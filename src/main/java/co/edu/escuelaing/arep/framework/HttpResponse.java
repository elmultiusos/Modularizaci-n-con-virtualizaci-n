package co.edu.escuelaing.arep.framework;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an HTTP response to be sent back through the socket.
 * Supports status codes, headers, and body content.
 */
public class HttpResponse {

    private final Socket clientSocket;
    private int statusCode = 200;
    private String statusText = "OK";
    private final Map<String, String> headers = new LinkedHashMap<>();
    private String body = "";

    public HttpResponse(Socket clientSocket) {
        this.clientSocket = clientSocket;
        headers.put("Content-Type", "text/html; charset=UTF-8");
        headers.put("Connection", "close");
    }

    public HttpResponse status(int code) {
        this.statusCode = code;
        this.statusText = getReasonPhrase(code);
        return this;
    }

    public HttpResponse header(String name, String value) {
        this.headers.put(name, value);
        return this;
    }

    public HttpResponse contentType(String type) {
        this.headers.put("Content-Type", type);
        return this;
    }

    public HttpResponse body(String body) {
        this.body = body;
        return this;
    }

    public void send() throws IOException {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        headers.put("Content-Length", String.valueOf(bodyBytes.length));

        StringBuilder response = new StringBuilder();
        response.append("HTTP/1.1 ").append(statusCode).append(" ").append(statusText).append("\r\n");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            response.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }
        response.append("\r\n");

        OutputStream out = clientSocket.getOutputStream();
        out.write(response.toString().getBytes(StandardCharsets.UTF_8));
        out.write(bodyBytes);
        out.flush();
    }

    public void sendJson(String jsonBody) throws IOException {
        contentType("application/json; charset=UTF-8");
        body(jsonBody);
        send();
    }

    private String getReasonPhrase(int code) {
        return switch (code) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            default -> "Unknown";
        };
    }
}
