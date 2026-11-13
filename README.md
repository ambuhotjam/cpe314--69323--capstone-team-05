<div align="center">

CPE 314 — Software Design

University of Cebu

Computer Engineering Department

# Restaurant Ordering & Inventory Insights

Project Design Document

</div>

<br>

| | |
|---|---|
| **Group Name / No.:** | Group 5 |
| **Members:** | Kiro Del Rosario- Team Leader |
| | Jamaica Ambuhot- Developer 1 |
| | Janine Ebrado- Developer 2 |
| | Josiela Yap- UI (User Interface) |
| | Vincent Hayag- DB Admin |
| | Ben Justin Lajot- QA (Quality Assurance) |
| **Instructor:** | Richyll Son |
| **Term:** | First Semester, S.Y. 2026-2027 |
| **Document Version / Date:** | Ver 1.0 - November 14, 2026 |

<br>


## TABLE OF CONTENTS

Revision History -----------------------------------------------------------------------------------------------i<br>

**1. Introduction & Project Overview--------------------------------------------------------------------------1**

**2. Preliminary ER Sketch & Team Roles ---------------------------------------------------------------------2**<br>
&nbsp;&nbsp;&nbsp;&nbsp;2.1 Preliminary Entity-Relationship Sketch ----------------------------------------------------------------2<br>
&nbsp;&nbsp;&nbsp;&nbsp;2.2 Team Roles----------------------------------------------------------------------------------------------3<br>

**3. System Architecture & Object-Oriented Design ---------------------------------------------------------3**<br>
&nbsp;&nbsp;&nbsp;&nbsp;3.1 Class Hierarchy -----------------------------------------------------------------------------------------3<br>
&nbsp;&nbsp;&nbsp;&nbsp;3.2 Single Responsibility Principle (SRP)-------------------------------------------------------------------4<br>
&nbsp;&nbsp;&nbsp;&nbsp;3.3 System Components -----------------------------------------------------------------------------------4<br>
&nbsp;&nbsp;&nbsp;&nbsp;3.4 Design Rationale ---------------------------------------------------------------------------------------4<br>
&nbsp;&nbsp;&nbsp;&nbsp;3.5 Data Structure and Recursive Ingredient Calculation -------------------------------------------------5<br>



### Revision History

**Table 1: Revision History**

| Version | Date | Author | Changes |
|---|---|---|---|
| 0.1 | October 20, 2026; October 25, 2026 | **Group 5**; **Ambuhot** – Introduction; **Ebrado** – ER Sketch; **Yap** – Feature Checklist; **Del Rosario** – Task Split; **Hayag** – The Public API; **Lajot** – Risk/Questions | Initial draft. Completed the Week 8 project proposal, including the introduction, ER sketch, feature checklist, task split, public API, and risks/questions. |





## 1. Introduction & Project Overview

DineSmart is a Java-based restaurant management system designed to help restaurant staff manage menu items, customer orders, table reservations, ingredients, and sales reports. It uses SQLite to store restaurant data and TheMealDB API to access recipes from different international cuisines. The system helps reduce manual work, improve order processing, and monitor ingredient availability. Its target users are restaurant owners, supervisors, cashiers, waiters, and kitchen staff.


## 2. Preliminary ER Sketch & Team Roles

### 2.1 Preliminary Entity-Relationship Sketch
<img width="5008" height="6688" alt="image" src="https://github.com/user-attachments/assets/619f8fd2-8468-4068-aa0c-9177366de30b" />
<p align="center">
  <em>Figure 1: Preliminary Entity-Relationship Sketch of DineSmart</em>
</p>

### 2.2 Team Roles

Table 2: Team Members and Responsibilities

| Member | Role |
|---|---|
| Kiro Del Rosario- Team Leader | API integration, authentication, access control, and system integration. |
| Jamaica Ambuhot- Developer 1 | Menu management, meal categories, recipe management, and menu recommendations. |
| Janine Ebrado- Developer 2 | SQLite database operations, order processing, and menu data storage. |
| Vincent Hayag- DB Admin | Kitchen queue, order history, inventory, and reservation management. |
| Josiela Yap- UI (User Interface) | JavaFX interface design, navigation, and image management. |
| Ben Justin Lajot- QA (Quality Assurance) | System testing, sales reporting, and data validation. |

## 3. System Architecture & Object-Oriented Design

### 3.1 Class Hierarchy
<img width="1888" height="968" alt="image" src="https://github.com/user-attachments/assets/9f020a50-a5be-4041-b834-44fdd35f60a8" />
<p align="center">
  <em>Figure 2: DineSmart Class Hierarchy</em>
</p>

### 3.2 Single Responsibility Principle (SRP)

Table 3: Classes and Their Responsibilities

| Class | Responsibility |
|---|---|
| MenuItem | Defines common menu item attributes and behavior. |
| Appetizer | Represents appetizer menu items. |
| MainCourse | Represents main course menu items. |
| Beverage | Represents beverage menu items. |

### 3.3 System Components

Table 4: DineSmart System Components and Responsibilities

| Package | Responsibility |
|---|---|
| model | Represents restaurant data and objects. |
| controller | Handles application operations and checks role permissions. |
| data | Manages SQLite database access and queries. |
| service | Handles recipes, ingredient calculations, staff permissions, and order queues. |
| dto | Represents data received from the external API. |
| ui | Displays the JavaFX interface and handles user interactions. |
| util | Provides shared utility functions, including money formatting. |

### 3.4 Design Rationale

DineSmart uses inheritance to allow different menu item categories to share common attributes and behavior. Each subclass is responsible for identifying its own item type, making the code easier to organize and maintain. The application also separates database operations, API requests, and business functions into different classes to reduce repeated code and improve maintainability. This structure supports the Single Responsibility Principle, although the main DineSmartApp class still handles several UI responsibilities.

### 3.5 Data Structures and Recursive Ingredient Calculation

DineSmart uses queues, stacks, and recursion to manage restaurant operations.

- **FIFO Queue:** KitchenQueue and PersistentKitchenQueue process kitchen orders in the order they were received.

- **LIFO Stack:** PendingOrderStack manages pending orders and allows the most recently added order to be removed first.

- **Recursive Ingredient Calculation:** RecursiveIngredientCalculator calculates the total ingredients required for multiple ordered menu items, including shared ingredients.

The recursive method processes one ordered menu item at a time and combines its ingredient requirements with the accumulated totals.

**Base Case:** The recursion stops when all ordered menu items have been processed.

**Recursive Case:** The method processes the current menu item's ingredients and calls itself to process the next ordered item.



