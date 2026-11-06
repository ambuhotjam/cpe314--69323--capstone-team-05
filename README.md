# DineSmart — Restaurant Ordering & Inventory Insights

CPE 314 · University of Cebu · Group 5

## Project structure

- `pom.xml` — Maven configuration (Java 17 release target)
- `src/main/java` — application source code
- `src/main/resources` — application resources and styles
- `src/test/java` — JUnit 5 tests

## Requirements

- JDK 17 or later
- Apache Maven 3.9 or later

## Run tests

From the repository root, run:

```bash
mvn test
```

## Run the JavaFX application

From the repository root, run:

```bash
mvn javafx:run
```

The application creates and initializes its SQLite database at runtime. The local database file is intentionally excluded from Git.
