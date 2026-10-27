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

