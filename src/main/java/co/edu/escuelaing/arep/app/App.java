package co.edu.escuelaing.arep.app;

import co.edu.escuelaing.arep.framework.WebServer;
import com.google.gson.JsonObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Main application that demonstrates the custom concurrent web framework.
 * Registers routes and starts the server on a configurable port.
 */
public class App {

    public static void main(String[] args) {
        int port = getPort();
        WebServer server = new WebServer(port);

        // Greeting endpoint - similar to Spring example
        server.get("/greeting", (req, res) -> {
            String name = req.getQueryParam("name", "World");
            String greeting = String.format("Hello, %s!", name);
            res.contentType("text/html; charset=UTF-8")
               .body("<html><body><h1>" + escapeHtml(greeting) + "</h1></body></html>")
               .send();
        });

        // REST API endpoint returning JSON
        server.get("/api/greeting", (req, res) -> {
            String name = req.getQueryParam("name", "World");
            JsonObject json = new JsonObject();
            json.addProperty("greeting", String.format("Hello, %s!", name));
            json.addProperty("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            res.sendJson(json.toString());
        });

        // Health check endpoint
        server.get("/api/health", (req, res) -> {
            JsonObject json = new JsonObject();
            json.addProperty("status", "UP");
            json.addProperty("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            res.sendJson(json.toString());
        });

        // Root page with links
        server.get("/", (req, res) -> {
            res.contentType("text/html; charset=UTF-8")
               .body(getIndexPage())
               .send();
        });

        // Hello endpoint
        server.get("/hello", (req, res) -> {
            res.contentType("text/html; charset=UTF-8")
               .body("<html><body><h1>Hello World!</h1></body></html>")
               .send();
        });

        server.start();
    }

    private static int getPort() {
        String portEnv = System.getenv("PORT");
        if (portEnv != null) {
            return Integer.parseInt(portEnv);
        }
        return 5000;
    }

    private static String escapeHtml(String input) {
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String getIndexPage() {
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Custom Web Framework - AREP</title>
                <style>
                    body { font-family: 'Segoe UI', Tahoma, sans-serif; max-width: 800px; margin: 50px auto; padding: 20px; background: #f5f5f5; }
                    .container { background: white; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                    h1 { color: #2c3e50; }
                    h2 { color: #34495e; }
                    a { color: #3498db; text-decoration: none; }
                    a:hover { text-decoration: underline; }
                    .endpoint { background: #ecf0f1; padding: 10px 15px; margin: 8px 0; border-radius: 5px; font-family: monospace; }
                    .form-group { margin: 15px 0; }
                    input[type="text"] { padding: 8px 12px; border: 1px solid #bdc3c7; border-radius: 5px; width: 200px; }
                    button { padding: 8px 20px; background: #3498db; color: white; border: none; border-radius: 5px; cursor: pointer; }
                    button:hover { background: #2980b9; }
                    #result { margin-top: 15px; padding: 15px; background: #e8f6f3; border-radius: 5px; display: none; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Custom Concurrent Web Framework</h1>
                    <p>Taller de Modularizacion con Virtualizacion - AREP</p>

                    <h2>Endpoints disponibles:</h2>
                    <div class="endpoint">GET <a href="/hello">/hello</a> - Hello World</div>
                    <div class="endpoint">GET <a href="/greeting?name=AREP">/greeting?name=AREP</a> - Saludo personalizado</div>
                    <div class="endpoint">GET <a href="/api/greeting?name=Docker">/api/greeting?name=Docker</a> - API JSON greeting</div>
                    <div class="endpoint">GET <a href="/api/health">/api/health</a> - Health check</div>

                    <h2>Prueba interactiva:</h2>
                    <div class="form-group">
                        <input type="text" id="nameInput" placeholder="Ingresa tu nombre">
                        <button onclick="greet()">Saludar</button>
                    </div>
                    <div id="result"></div>
                </div>
                <script>
                    function greet() {
                        const name = document.getElementById('nameInput').value || 'World';
                        fetch('/api/greeting?name=' + encodeURIComponent(name))
                            .then(r => r.json())
                            .then(data => {
                                const div = document.getElementById('result');
                                div.style.display = 'block';
                                div.innerHTML = '<strong>' + data.greeting + '</strong><br><small>Timestamp: ' + data.timestamp + '</small>';
                            });
                    }
                </script>
            </body>
            </html>
            """;
    }
}
