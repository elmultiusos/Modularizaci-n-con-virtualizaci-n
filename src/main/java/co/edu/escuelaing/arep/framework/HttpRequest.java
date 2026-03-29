package co.edu.escuelaing.arep.framework;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an HTTP request parsed from a raw socket connection.
 * Extracts method, path, query parameters, headers, and body.
 */
public class HttpRequest {

    private String method;
    private String path;
    private String fullUri;
    private final Map<String, String> queryParams = new HashMap<>();
    private final Map<String, String> headers = new HashMap<>();
    private String body;

    public HttpRequest(Socket clientSocket) throws IOException {
        parse(clientSocket);
    }

    private void parse(Socket clientSocket) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));

        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            this.method = "GET";
            this.path = "/";
            this.fullUri = "/";
            return;
        }

        String[] parts = requestLine.split(" ");
        this.method = parts[0].toUpperCase();
        this.fullUri = parts.length > 1 ? parts[1] : "/";

        parseUri(this.fullUri);

        String headerLine;
        while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
            int colonIndex = headerLine.indexOf(":");
            if (colonIndex > 0) {
                String key = headerLine.substring(0, colonIndex).trim().toLowerCase();
                String value = headerLine.substring(colonIndex + 1).trim();
                headers.put(key, value);
            }
        }

        if (headers.containsKey("content-length")) {
            int contentLength = Integer.parseInt(headers.get("content-length"));
            char[] bodyChars = new char[contentLength];
            int totalRead = 0;
            while (totalRead < contentLength) {
                int read = reader.read(bodyChars, totalRead, contentLength - totalRead);
                if (read == -1) break;
                totalRead += read;
            }
            this.body = new String(bodyChars, 0, totalRead);
        }
    }

    private void parseUri(String uri) {
        int queryIndex = uri.indexOf("?");
        if (queryIndex >= 0) {
            this.path = uri.substring(0, queryIndex);
            String queryString = uri.substring(queryIndex + 1);
            for (String param : queryString.split("&")) {
                String[] kv = param.split("=", 2);
                String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
                queryParams.put(key, value);
            }
        } else {
            this.path = uri;
        }
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getFullUri() {
        return fullUri;
    }

    public String getQueryParam(String name) {
        return queryParams.get(name);
    }

    public String getQueryParam(String name, String defaultValue) {
        return queryParams.getOrDefault(name, defaultValue);
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public String getHeader(String name) {
        return headers.get(name.toLowerCase());
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }
}
