package co.edu.escuelaing.arep.framework;

/**
 * Functional interface for defining route handlers.
 * Each handler receives a request and response, and can throw IOException.
 */
@FunctionalInterface
public interface RouteHandler {
    void handle(HttpRequest request, HttpResponse response) throws Exception;
}
