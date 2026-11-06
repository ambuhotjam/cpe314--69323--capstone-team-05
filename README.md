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
## TABLE OF CONTENTS

Revision History ---------------------------------------------------------------------------------------------------------------------------------- i

**1. Introduction & Project Overview--------------------------------------------------------------------------------------------------------------1**

**2. Preliminary ER Sketch & Team Roles ---------------------------------------------------------------------------------------------------------2**<br>
&nbsp;&nbsp;&nbsp;&nbsp;2.1 Preliminary Entity-Relationship (ER) Sketch------------------------------------------------------------------------------------------------2<br>
&nbsp;&nbsp;&nbsp;&nbsp;2.2 Team Roles and Responsibilities------------------------------------------------------------------------------------------------------------3

**3. System Architecture & Object-Oriented Design---------------------------------------------------------------------------------------------4**<br>
&nbsp;&nbsp;&nbsp;&nbsp;3.1 System Architecture Overview -------------------------------------------------------------------------------------------------------------4<br>
&nbsp;&nbsp;&nbsp;&nbsp;3.2 Object-Oriented Design (OOD) ------------------------------------------------------------------------------------------------------------5<br>
&nbsp;&nbsp;&nbsp;&nbsp;3.3 MenuItem Class Hierarchy -----------------------------------------------------------------------------------------------------------------6<br>
&nbsp;&nbsp;&nbsp;&nbsp;3.4 Application Components and Responsibilities --------------------------------------------------------------------------------------------7

**4. Data Model & Database Design --------------------------------------------------------------------------------------------------------------8**<br>
&nbsp;&nbsp;&nbsp;&nbsp;4.1 Database Overview -------------------------------------------------------------------------------------------------------------------------8<br>
&nbsp;&nbsp;&nbsp;&nbsp;4.2 Entity and Attribute Definitions ------------------------------------------------------------------------------------------------------------9<br>
&nbsp;&nbsp;&nbsp;&nbsp;4.3 Primary Keys and Foreign Keys ------------------------------------------------------------------------------------------------------------10<br>
&nbsp;&nbsp;&nbsp;&nbsp;4.4 Database Relationships --------------------------------------------------------------------------------------------------------------------11<br>
&nbsp;&nbsp;&nbsp;&nbsp;4.5 SQLite Database Structure -----------------------------------------------------------------------------------------------------------------12

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