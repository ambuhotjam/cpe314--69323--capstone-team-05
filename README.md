## Project Structure

- `pom.xml` — Maven configuration (Java 17 release target)
- `src/main/java/` — Application source code
- `src/main/resources/` — Application resources and styles
- `src/test/java/` — JUnit 5 tests

## Table of Contents

1. [Revision History](#revision-history)
2. [Introduction & Project Overview](#introduction--project-overview)
3. [Preliminary ER Sketch & Team Roles](#preliminary-er-sketch--team-roles)
4. [System Architecture & Object-Oriented Design](#system-architecture--object-oriented-design)
5. [Data Model & Database Design](#data-model--database-design)

## Requirements

- JDK 17 or later
- Apache Maven 3.9 or later

## Run Tests

From the repository root, run:

```bash
mvn clean test
```

## Run the JavaFX Application

From the repository root, run:

```bash
mvn javafx:run
```

The application creates and initializes its SQLite database at runtime. The local database file is intentionally excluded from Git.