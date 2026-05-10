import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BookServer {

    static BookService service = new BookService();
    static final Path WEB_ROOT = Paths.get("src/web");

    public static void main(String[] args) throws Exception {

        HttpServer server = HttpServer.create(new InetSocketAddress(9090), 0);
        server.createContext("/", BookServer::handleStaticFile);

        server.createContext("/books", (exchange) -> {

            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            String method = exchange.getRequestMethod();

            if (method.equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (method.equals("GET")) {
                sendJson(exchange, 200, booksToJson(service.getBooks()));
            }

            else if (method.equals("POST")) {
                try {
                    Book b = parseBook(readRequestBody(exchange));
                    String validationError = validateBook(b);

                    if (validationError != null) {
                        sendJson(exchange, 400, messageJson(validationError));
                        return;
                    }

                    service.addBook(b);
                    sendJson(exchange, 201, messageJson("Added"));
                } catch (IllegalArgumentException e) {
                    sendJson(exchange, 400, messageJson("Invalid JSON"));
                }
            }

            else if (method.equals("PUT")) {
                String query = exchange.getRequestURI().getQuery();
                Integer id = parseId(query);

                if (id == null) {
                    sendJson(exchange, 400, messageJson("Invalid or missing id"));
                    return;
                }

                try {
                    int progress = parseProgress(readRequestBody(exchange));
                    Book existingBook = findBookById(id);

                    if (existingBook == null) {
                        sendJson(exchange, 404, messageJson("Book not found"));
                        return;
                    }

                    if (progress < 0 || progress > existingBook.totalPages) {
                        sendJson(exchange, 400, messageJson("Progress must be between 0 and total pages"));
                        return;
                    }

                    Book updatedBook = service.updateProgress(id, progress);
                    sendJson(exchange, 200, bookToJson(updatedBook));
                } catch (IllegalArgumentException e) {
                    sendJson(exchange, 400, messageJson("Invalid JSON"));
                }
            }

            else if (method.equals("DELETE")) {
                String query = exchange.getRequestURI().getQuery();
                Integer id = parseId(query);

                if (id == null) {
                    sendJson(exchange, 400, messageJson("Invalid or missing id"));
                    return;
                }

                boolean deleted = service.deleteBook(id);

                if (!deleted) {
                    sendJson(exchange, 404, messageJson("Book not found"));
                    return;
                }

                sendJson(exchange, 200, messageJson("Deleted"));
            } else {
                sendJson(exchange, 405, messageJson("Method not allowed"));
            }
        });

        server.start();
        System.out.println("Server running at http://localhost:9090");
    }

    private static String validateBook(Book book) {
        if (book == null) return "Missing book data";
        if (book.title == null || book.title.trim().isEmpty()) return "Title is required";
        if (book.author == null || book.author.trim().isEmpty()) return "Author is required";
        if (book.totalPages <= 0) return "Pages must be greater than 0";
        if (book.progress < 0) return "Progress cannot be negative";
        if (book.progress > book.totalPages) return "Progress cannot exceed total pages";
        return null;
    }

    private static Integer parseId(String query) {
        if (query == null || query.trim().isEmpty()) return null;

        for (String part : query.split("&")) {
            String[] pieces = part.split("=", 2);
            if (pieces.length == 2 && pieces[0].equals("id")) {
                try {
                    return Integer.parseInt(pieces[1]);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }

        return null;
    }

    private static Book findBookById(int id) {
        for (Book book : service.getBooks()) {
            if (book.id == id) {
                return book;
            }
        }

        return null;
    }

    private static void handleStaticFile(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/".equals(path)) {
            path = "/index.html";
        }

        Path requested = WEB_ROOT.resolve(path.substring(1)).normalize();
        if (!requested.startsWith(WEB_ROOT) || !Files.exists(requested) || Files.isDirectory(requested)) {
            sendPlainText(exchange, 404, "Not found", "text/plain; charset=UTF-8");
            return;
        }

        String contentType = guessContentType(requested.getFileName().toString());
        byte[] bytes = Files.readAllBytes(requested);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String guessContentType(String filename) {
        if (filename.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filename.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filename.endsWith(".js")) return "application/javascript; charset=UTF-8";
        return "application/octet-stream";
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Book parseBook(String json) {
        String title = extractString(json, "title");
        String author = extractString(json, "author");
        Integer totalPages = extractInt(json, "totalPages");
        Integer progress = extractInt(json, "progress");

        if (title == null || author == null || totalPages == null || progress == null) {
            throw new IllegalArgumentException("Missing required field");
        }

        return new Book(0, title, author, totalPages, progress);
    }

    private static int parseProgress(String json) {
        Integer progress = extractInt(json, "progress");
        if (progress == null) {
            throw new IllegalArgumentException("Missing progress");
        }

        return progress;
    }

    private static String extractString(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\\\\\"])*)\"");
        Matcher matcher = pattern.matcher(json);

        if (!matcher.find()) {
            return null;
        }

        return unescapeJson(matcher.group(1));
    }

    private static Integer extractInt(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(-?\\d+)");
        Matcher matcher = pattern.matcher(json);

        if (!matcher.find()) {
            return null;
        }

        return Integer.parseInt(matcher.group(1));
    }

    private static String booksToJson(List<Book> books) {
        StringBuilder json = new StringBuilder("[");

        for (int i = 0; i < books.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(bookToJson(books.get(i)));
        }

        json.append(']');
        return json.toString();
    }

    private static String bookToJson(Book book) {
        return "{"
                + "\"id\":" + book.id + ","
                + "\"title\":\"" + escapeJson(book.title) + "\","
                + "\"author\":\"" + escapeJson(book.author) + "\","
                + "\"totalPages\":" + book.totalPages + ","
                + "\"progress\":" + book.progress + ","
                + "\"progressPercentage\":" + book.getProgressPercentage()
                + "}";
    }

    private static String messageJson(String message) {
        return "{\"message\":\"" + escapeJson(message) + "\"}";
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String value) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case '"':
                        result.append('"');
                        break;
                    case '\\':
                        result.append('\\');
                        break;
                    case 'n':
                        result.append('\n');
                        break;
                    case 'r':
                        result.append('\r');
                        break;
                    case 't':
                        result.append('\t');
                        break;
                    default:
                        result.append(next);
                        break;
                }
            } else {
                result.append(current);
            }
        }

        return result.toString();
    }

    private static void sendJson(HttpExchange ex, int statusCode, String payload) throws IOException {
        sendBytes(ex, statusCode, payload.getBytes(StandardCharsets.UTF_8), "application/json; charset=UTF-8");
    }

    private static void sendPlainText(HttpExchange ex, int statusCode, String response, String contentType) throws IOException {
        sendBytes(ex, statusCode, response.getBytes(StandardCharsets.UTF_8), contentType);
    }

    private static void sendBytes(HttpExchange ex, int statusCode, byte[] bytes, String contentType) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
