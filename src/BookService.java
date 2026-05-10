import java.io.*;
import java.util.*;

public class BookService {

    private List<Book> books = new ArrayList<>();
    private static final String FILE = "books.txt";
    private int currentId = 1;

    public BookService() {
        load();
    }

    public synchronized List<Book> getBooks() {
        return new ArrayList<>(books);
    }

    public synchronized void addBook(Book b) {
        b.id = currentId++;
        b.title = b.title.trim();
        b.author = b.author.trim();
        books.add(b);
        save();
    }

    public synchronized boolean deleteBook(int id) {
        boolean removed = books.removeIf(b -> b.id == id);
        save();
        return removed;
    }

    public synchronized Book updateProgress(int id, int progress) {
        for (Book book : books) {
            if (book.id == id) {
                book.progress = progress;
                save();
                return book;
            }
        }

        return null;
    }

    private void save() {
        try (Writer writer = new FileWriter(FILE)) {
            for (Book book : books) {
                writer.write(String.join("\t",
                        String.valueOf(book.id),
                        escape(book.title),
                        escape(book.author),
                        String.valueOf(book.totalPages),
                        String.valueOf(book.progress)));
                writer.write(System.lineSeparator());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void load() {
        try {
            File file = new File(FILE);
            if (!file.exists()) return;

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                books = new ArrayList<>();
                String line;

                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    String[] parts = line.split("\t", -1);
                    if (parts.length != 5) {
                        continue;
                    }

                    try {
                        Book b = new Book(
                                Integer.parseInt(parts[0]),
                                unescape(parts[1]),
                                unescape(parts[2]),
                                Integer.parseInt(parts[3]),
                                Integer.parseInt(parts[4]));
                        books.add(b);
                        currentId = Math.max(currentId, b.id + 1);
                    } catch (NumberFormatException ignored) {
                        // Skip malformed lines instead of failing startup.
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private String unescape(String value) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case 't':
                        result.append('\t');
                        break;
                    case 'r':
                        result.append('\r');
                        break;
                    case 'n':
                        result.append('\n');
                        break;
                    case '\\':
                        result.append('\\');
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
}
