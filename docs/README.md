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
| **Document Version / Date:** | Ver 0.1 - October 20, 2026 |

<br>

## Table of Contents

```text
Revision History ......................................... 3
1. Introduction & Project Overview ....................... 4
2. Preliminary ER Sketch & Team Roles .................... 5
    Team Roles ........................................... 6
3. System Architecture & Object-Oriented Design .......... 7
    SRP Justification .................................... 7
4. Data Model & Database Design .......................... 7
    Schema Description ................................... 7
```

## Revision History

Table 1: Revision history

| Version | Date | Author | Changes |
|:---|:---|:---|:---|
| 0.1 | October 20, 2026<br>October 25, 2026 | Group 5<br>**Ambuhot** - Introduction<br>**Ebrado** - ER Sketch<br>**Yap** - Feature Checklist<br>**Del Rosario** - Task Split<br>**Hayag** - The Public API<br>**Lajot** - Risk/Questions | Initial draft<br><br>Completed the Week 8 project proposal, including the introduction, ER sketch, feature checklist, task split, public API, and risks/questions. |
| 0.2 | October 26, 2026 | **Yap & Hayag** - Class Hierarchy Diagram/Description<br>**Ambuhot** - SRP Justification<br>**Ebrado** - Finalized ERD<br>**Del Rosario & Lajot** - Schema Description | Added Week 9 design and documentation sections, including the Class Hierarchy Diagram, SRP Justification, Finalized ERD, and Schema Description. |
| 0.3 | | | Week 10 sections added |
| 1.0 | | | Final compilation and polish - Week 11 |


## Introduction & Project Overview
The Restaurant Ordering & Inventory Insights is a JavaFX desktop application designed for small restaurants and campus canteens that need an efficient way to manage orders and ingredient inventory. The application will manage menu items, nested meal combos, and the kitchen order queue while integrating with TheMealDB to import real recipe information. It will use SQLite to store records and generate charts showing best-selling menu items and peak ordering hours, helping staff improve food preparation and inventory management.

## Preliminary ER Sketch & Team Roles
<img width="4008" height="3168" alt="image" src="https://github.com/user-attachments/assets/3ec183e8-4a63-4e80-bec8-cbe5d00a00a3" />
<p align="center">
  <em>Figure 1: Restaurant Ordering & Inventory System Diagram</em>
</p>

## Team Roles

Table 1: Team members and proposed responsibilities

| Member | Role |
|---|---|
| Kiro Del Rosario - Team Leader | TheMealDB integration and background CSV-import plan. |
| Jamaica Ambuhot - Developer 1 | OOD hierarchy and recursive combo pricing plan. |
| Janine Ebrado - Developer 2 | ER sketch and SQLite/JDBC data-layer plan. |
| Vincent Hayag - DB Admin | Kitchen Queue, cancellation Stack and stock-flow plan. |
| Josiela Yap - UI (User Interface) | JavaFX screen sketch and event-handler plan. |
| Ben Justin Lajot - QA (Quality Assurance) | JUnit test cases, sales aggregation and chart plan. |

## System Architecture &amp; Object-Oriented Design
<img width="1536" height="1024" alt="w9 image" src="https://github.com/user-attachments/assets/3434b7a4-1d16-4221-a953-4759aa68da1a" />
<p align="center">
 <em>Figure 2: Class Diagram for Restaurant Menu Items</em>
 </p>
The class hierarchy diagram illustrates the inheritance relationships among the menu-related classes of
the Restaurant Ordering &amp; Inventory Insights application. It consists of one abstract superclass,
MenuItem, and four concrete subclasses: Appetizer, MainCourse, Beverage, and Combo. This structure
organizes menu items according to their categories while promoting code reusability and
maintainability.

1. MenuItem (Abstract Superclass)
The MenuItem class serves as the abstract superclass for all menu items in the application. It stores and
validates common menu-item information and provides a shared structure for its subclasses. This design
reduces code duplication and establishes a consistent way of representing menu items throughout the
application.

2. Appetizer (Concrete Subclass)
The Appetizer class extends the MenuItem class and represents appetizer items offered by the
restaurant. It inherits the common menu-item information and behavior defined by its superclass while
identifying items belonging to the appetizer category. This allows appetizer items to be managed
consistently with other menu categories.

3. MainCourse (Concrete Subclass)
The MainCourse class extends the MenuItem class and represents the main-course items available on
the restaurant&#39;s menu. It inherits the common menu-item information and behavior defined by its
superclass while distinguishing main-course items from appetizers, beverages, and combos.

4. Beverage (Concrete Subclass)
The Beverage class extends the MenuItem class and represents beverages offered by the restaurant. It
inherits the common menu-item information and behavior defined by its superclass while identifying
items belonging to the beverage category. This maintains a consistent structure for managing different
types of menu items.

5. Combo (Concrete Subclass)
The Combo class extends the MenuItem class and represents combinations of individual menu items or
other combos. It maintains combo components, recursively calculates the total price of nested combos,
validates component quantities, and prevents circular references. These responsibilities allow the
application to support nested combos while maintaining valid component relationships and accurate
price calculations.

The class hierarchy helps organize the different types of menu items in the system. The MenuItem class
serves as the parent class, while Appetizer, MainCourse, Beverage, and Combo represent the different
menu categories. This setup allows the classes to share common information instead of repeating the
same code. The Combo class can also handle combinations of menu items, including combos within
other combos, and calculate their total prices. Overall, this structure makes the menu easier to manage
and supports the system&#39;s ordering and pricing features.
