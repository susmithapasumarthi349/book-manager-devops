public class Book {
    public int id;
    public String title;
    public String author;
    public int totalPages;
    public int progress;

    public Book(int id, String title, String author, int totalPages, int progress) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.totalPages = totalPages;
        this.progress = progress;
    }

    public int getProgressPercentage() {
        if (totalPages <= 0) {
            return 0;
        }

        return Math.min(100, Math.max(0, (progress * 100) / totalPages));
    }
}
