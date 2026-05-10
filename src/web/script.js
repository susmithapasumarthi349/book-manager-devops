const API_URL = "/books";
const state = {
  books: [],
  filter: "all"
};

function setMessage(text, isError = false) {
  const message = document.getElementById("message");
  message.textContent = text;
  message.classList.toggle("error", isError);
}

function getStatus(book) {
  if (book.progress <= 0) {
    return "unread";
  }

  if (book.progress >= book.totalPages) {
    return "completed";
  }

  return "reading";
}

function updateStats(books) {
  const total = books.length;
  const reading = books.filter((book) => getStatus(book) === "reading").length;
  const completed = books.filter((book) => getStatus(book) === "completed").length;
  const unread = books.filter((book) => getStatus(book) === "unread").length;
  const totalPagesRead = books.reduce((sum, book) => sum + book.progress, 0);
  const totalPages = books.reduce((sum, book) => sum + book.totalPages, 0);
  const completionRate = totalPages === 0 ? 0 : Math.round((totalPagesRead / totalPages) * 100);

  document.getElementById("total").textContent = total;
  document.getElementById("reading").textContent = reading;
  document.getElementById("completed").textContent = completed;
  document.getElementById("unread").textContent = unread;
  document.getElementById("heroPagesRead").textContent = totalPagesRead;
  document.getElementById("heroCompletionRate").textContent = `${completionRate}%`;
}

function createActionButton(label, onClick, variant = "") {
  const button = document.createElement("button");
  button.type = "button";
  button.textContent = label;
  button.className = variant;
  button.addEventListener("click", onClick);
  return button;
}

function createBookCard(book) {
  const card = document.createElement("article");
  card.className = "card";

  const status = getStatus(book);
  const percentage = Math.max(0, Math.min(100, Math.round((book.progress / book.totalPages) * 100)));

  const badge = document.createElement("span");
  badge.className = `badge ${status}`;
  badge.textContent = status === "completed" ? "Completed" : status === "reading" ? "Reading" : "Unread";

  const title = document.createElement("h3");
  title.textContent = book.title;

  const author = document.createElement("p");
  author.className = "book-author";
  author.textContent = book.author;

  const progressMeta = document.createElement("div");
  progressMeta.className = "progress-meta";
  progressMeta.innerHTML = `<span>${book.progress}/${book.totalPages} pages</span><strong>${percentage}%</strong>`;

  const progress = document.createElement("div");
  progress.className = "progress";

  const progressBar = document.createElement("div");
  progressBar.className = "progress-bar";
  progressBar.style.width = `${percentage}%`;
  progress.appendChild(progressBar);

  const actions = document.createElement("div");
  actions.className = "card-actions";

  actions.appendChild(
    createActionButton("-10", () => updateProgress(book.id, Math.max(0, book.progress - 10)))
  );
  actions.appendChild(
    createActionButton("+10", () => updateProgress(book.id, Math.min(book.totalPages, book.progress + 10)))
  );
  actions.appendChild(
    createActionButton("Finish", () => updateProgress(book.id, book.totalPages), "secondary-button")
  );
  actions.appendChild(
    createActionButton("Delete", () => deleteBook(book.id), "danger-button")
  );

  card.appendChild(badge);
  card.appendChild(title);
  card.appendChild(author);
  card.appendChild(progressMeta);
  card.appendChild(progress);
  card.appendChild(actions);

  return card;
}

function getVisibleBooks() {
  const searchText = document.getElementById("search").value.trim().toLowerCase();

  return state.books.filter((book) => {
    const matchesSearch = `${book.title} ${book.author}`.toLowerCase().includes(searchText);
    const matchesFilter = state.filter === "all" || getStatus(book) === state.filter;
    return matchesSearch && matchesFilter;
  });
}

function renderBooks() {
  const list = document.getElementById("bookList");
  list.innerHTML = "";

  const visibleBooks = getVisibleBooks();
  updateStats(state.books);

  if (visibleBooks.length === 0) {
    const emptyState = document.createElement("div");
    emptyState.className = "empty-state";
    emptyState.innerHTML = state.books.length === 0
      ? "<h3>Your shelf is empty</h3><p>Add your first book to start building momentum.</p>"
      : "<h3>No matches found</h3><p>Try a different search or switch the status filter.</p>";
    list.appendChild(emptyState);
    return;
  }

  visibleBooks.forEach((book) => {
    list.appendChild(createBookCard(book));
  });
}

async function loadBooks() {
  try {
    const res = await fetch(API_URL);
    if (!res.ok) {
      throw new Error("Request failed");
    }

    state.books = await res.json();
    setMessage("");
    renderBooks();
  } catch (error) {
    state.books = [];
    renderBooks();
    setMessage("Could not load books. Start the Java server and open http://localhost:9090.", true);
  }
}

async function addBook(event) {
  event.preventDefault();

  const form = document.getElementById("bookForm");
  const formData = new FormData(form);
  const title = String(formData.get("title") || "").trim();
  const author = String(formData.get("author") || "").trim();
  const pages = Number.parseInt(String(formData.get("pages") || ""), 10);
  const progress = Number.parseInt(String(formData.get("progress") || ""), 10);

  if (!title || !author || Number.isNaN(pages) || Number.isNaN(progress)) {
    setMessage("Enter valid details in every field.", true);
    return;
  }

  if (pages <= 0 || progress < 0 || progress > pages) {
    setMessage("Progress must stay between 0 and total pages.", true);
    return;
  }

  try {
    const res = await fetch(API_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title, author, totalPages: pages, progress })
    });

    const result = await res.json();
    if (!res.ok) {
      setMessage(result.message || "Unable to add the book.", true);
      return;
    }

    form.reset();
    setMessage(`Added "${title}" to your shelf.`);
    await loadBooks();
  } catch (error) {
    setMessage("Could not add the book. Start the Java server and try again.", true);
  }
}

async function updateProgress(id, progress) {
  try {
    const res = await fetch(`${API_URL}?id=${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ progress })
    });

    const result = await res.json();
    if (!res.ok) {
      setMessage(result.message || "Unable to update progress.", true);
      return;
    }

    const targetIndex = state.books.findIndex((book) => book.id === id);
    if (targetIndex >= 0) {
      state.books[targetIndex] = result;
    }

    setMessage("Reading progress updated.");
    renderBooks();
  } catch (error) {
    setMessage("Could not update progress. Start the Java server and try again.", true);
  }
}

async function deleteBook(id) {
  try {
    const res = await fetch(`${API_URL}?id=${id}`, { method: "DELETE" });
    const result = await res.json();

    if (!res.ok) {
      setMessage(result.message || "Unable to delete the book.", true);
      return;
    }

    state.books = state.books.filter((book) => book.id !== id);
    setMessage("Book deleted.");
    renderBooks();
  } catch (error) {
    setMessage("Could not delete the book. Start the Java server and try again.", true);
  }
}

document.getElementById("bookForm").addEventListener("submit", addBook);
document.getElementById("search").addEventListener("input", renderBooks);

document.querySelectorAll(".filter-chip").forEach((button) => {
  button.addEventListener("click", () => {
    state.filter = button.dataset.filter;
    document.querySelectorAll(".filter-chip").forEach((chip) => chip.classList.remove("active"));
    button.classList.add("active");
    renderBooks();
  });
});

loadBooks();
