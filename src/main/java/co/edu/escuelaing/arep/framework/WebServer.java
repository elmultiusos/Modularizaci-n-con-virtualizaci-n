package co.edu.escuelaing.arep.framework;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Custom concurrent HTTP web server framework.
 * Uses a thread pool (ExecutorService) to handle requests concurrently.
 * Supports graceful shutdown via shutdown hook.
 */
public class WebServer {

    private static final Logger LOGGER = Logger.getLogger(WebServer.class.getName());

    private final int port;
    private final int threadPoolSize;
    private final Map<String, RouteHandler> getRoutes = new ConcurrentHashMap<>();
    private final Map<String, RouteHandler> postRoutes = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private String staticFilesPath = "/static";

    public WebServer(int port) {
        this.port = port;
        this.threadPoolSize = Runtime.getRuntime().availableProcessors() * 2;
    }

    public WebServer(int port, int threadPoolSize) {
        this.port = port;
        this.threadPoolSize = threadPoolSize;
    }

    /**
     * Register a GET route.
     */
    public WebServer get(String path, RouteHandler handler) {
        getRoutes.put(path, handler);
        return this;
    }

    /**
     * Register a POST route.
     */
    public WebServer post(String path, RouteHandler handler) {
        postRoutes.put(path, handler);
        return this;
    }

    /**
     * Set the base path for static files in the classpath.
     */
    public WebServer staticFiles(String path) {
        this.staticFilesPath = path;
        return this;
    }

    /**
     * Start the server. This method blocks until the server is stopped.
     * Uses a fixed thread pool for concurrent request handling.
     * Registers a shutdown hook for graceful shutdown.
     */
    public void start() {
        running.set(true);
        threadPool = Executors.newFixedThreadPool(threadPoolSize);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutdown hook triggered. Stopping server gracefully...");
            stop();
        }));

        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(1000);
            LOGGER.info("Server started on port " + port + " with " + threadPoolSize + " worker threads");
            LOGGER.info("Access the application at http://localhost:" + port + "/");

            while (running.get()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.submit(() -> handleClient(clientSocket));
                } catch (SocketTimeoutException e) {
                    // Timeout allows checking the running flag periodically
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                LOGGER.log(Level.SEVERE, "Server error", e);
            }
        } finally {
            cleanup();
        }
    }

    /**
     * Gracefully stop the server.
     * Waits for in-flight requests to finish before shutting down.
     */
    public void stop() {
        LOGGER.info("Stopping server...");
        running.set(false);

        if (threadPool != null) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                    LOGGER.warning("Thread pool did not terminate in time. Forcing shutdown...");
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        cleanup();
        LOGGER.info("Server stopped.");
    }

    private void cleanup() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error closing server socket", e);
        }
    }

    private void handleClient(Socket clientSocket) {
        try {
            HttpRequest request = new HttpRequest(clientSocket);
            HttpResponse response = new HttpResponse(clientSocket);

            LOGGER.info(request.getMethod() + " " + request.getPath()
                    + " from " + clientSocket.getInetAddress());

            RouteHandler handler = findHandler(request);
            if (handler != null) {
                handler.handle(request, response);
            } else if (tryServeStaticFile(request, response)) {
                // Static file served successfully
            } else {
                response.status(404)
                        .contentType("text/html; charset=UTF-8")
                        .body("<html><body><h1>404 - Not Found</h1>"
                                + "<p>The path <code>" + escapeHtml(request.getPath())
                                + "</code> was not found.</p></body></html>")
                        .send();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error handling request", e);
            try {
                HttpResponse errorResponse = new HttpResponse(clientSocket);
                errorResponse.status(500)
                        .body("<html><body><h1>500 - Internal Server Error</h1></body></html>")
                        .send();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Error sending error response", ex);
            }
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing client socket", e);
            }
        }
    }

    private RouteHandler findHandler(HttpRequest request) {
        Map<String, RouteHandler> routes = switch (request.getMethod()) {
            case "GET" -> getRoutes;
            case "POST" -> postRoutes;
            default -> null;
        };
        if (routes == null) return null;
        return routes.get(request.getPath());
    }

    private boolean tryServeStaticFile(HttpRequest request, HttpResponse response) throws IOException {
        String filePath = staticFilesPath + request.getPath();
        if (filePath.contains("..")) {
            return false;
        }

        try (InputStream is = getClass().getResourceAsStream(filePath)) {
            if (is == null) return false;

            byte[] content = is.readAllBytes();
            String contentType = guessContentType(filePath);
            response.contentType(contentType)
                    .body(new String(content, StandardCharsets.UTF_8))
                    .send();
            return true;
        }
    }

    private String guessContentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=UTF-8";
        if (path.endsWith(".css")) return "text/css; charset=UTF-8";
        if (path.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (path.endsWith(".json")) return "application/json; charset=UTF-8";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "text/plain; charset=UTF-8";
    }

    private String escapeHtml(String input) {
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
