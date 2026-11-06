| **Group Name / No.:**        | Group 5                                                                                                                                  |
|:-----------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------|
| **Members:**                 | Kiro Del RosarioJamaica Ambuhot - Team Leader                                                                                                           |
|                              | Jamaica Ambuhot - Developer 1                                                                                                             |
|                              | Janine Ebrado - Developer 2                                                                                                      |
|                              | Vincent Hayag - DB Admin                                                                                                                 |
|                              | Josiela Yap - UI (User Interface)                                                                                                           |
|                              | Ben Justin Lajot - QA (Quality Assurance)                                                                                                |
| **Repository URL:**          | [https://github.com/ambuhotjam/cpe314--69323--capstone-team-05.git](https://github.com/ambuhotjam/cpe314--69323--capstone-team-05.git)   |
| **Theme:**                   | Restaurant Ordering & Inventory Insights                                                                                                 |
| **API You Will Use:**        | TheMealDB - [https://www.themealdb.com/api/json/v1/1/](https://www.themealdb.com/api/json/v1/1/)                                         |
| **Instructor:**              | Richyll Son                                                                                                                              |
| **Term:**                    | First Semester, S.Y. 2026-2027                                                                                                           |
| **Document Version / Date:** | Ver 0.1 - October 20, 2026                                                                                                               |


## TABLE OF CONTENTS

Revision History ---------------------------------------------------------------------------------------------------------------------------------- i

**1. Introduction & Project Overview--------------------------------------------------------------------------------------------------------------1**

**2. Preliminary ER Sketch, Feature Checklist & Team Roles ------------------------------------------------------------------------------------2**<br>
&nbsp;&nbsp;&nbsp;&nbsp;2.1 Preliminary Entity-Relationship (ER) Sketch------------------------------------------------------------------------------------------------2<br>
&nbsp;&nbsp;&nbsp;&nbsp;2.2 Feature Checklist----------------------------------------------------------------------------------------------------------------------------3     
&nbsp;&nbsp;&nbsp;&nbsp;2.3 Team Roles and Responsibilities------------------------------------------------------------------------------------------------------------3


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


## 1. Introduction & Project Overview

The Restaurant Ordering & Inventory Insights is a JavaFX desktop application designed for small restaurants and campus canteens that need an efficient way to manage orders and ingredient inventory. The application will manage menu items, nested meal combos, and the kitchen order queue while integrating with TheMealDB to import real recipe information. It will use SQLite to store records and generate charts showing best-selling menu items and peak ordering hours, helping staff improve food preparation and inventory management.



## 2. Preliminary ER Sketch & Team Roles

### 2.1 Preliminary Entity-Relationship (ER) Sketch
<img width="4008" height="3168" alt="image" src="https://github.com/user-attachments/assets/d84a0066-fac9-4fba-89e4-9881149f5ac2" />
<p align="center">
  <em>Figure 1: Restaurant Ordering & Inventory System Diagram</em>
</p>

### 2.2 Feature Checklist
| Planned Feature | Requirement(s) It Satisfies |
| :--- | :--- |
| MenuItem abstract class with Appetizer, MainCourse and Beverage subclasses; different preparation estimates; separate services. | Object-Oriented Design |
| FIFO kitchen Queue, latest-pending-order Stack, and recursive total for combos nested at any depth; reject cycles. | Data Structure / Recursion |
| At least 6 JUnit 5 tests: totals, invalid quantity, FIFO, cancellation, nested combos and insufficient stock. | Automated Testing |
| Related SQLite tables; JDBC PreparedStatement CRUD and JOIN queries. | Data Modeling + Database |
| Search TheMealDB and save selected meal names and real ingredient lists after staff review. | API Consumption |
| JavaFX Place Order, Cancel Latest Pending Order, Search Recipe and Import History controls with handlers. | Event-Driven GUI |
| Import a large historical order CSV in Task.call(); report progress and update UI on the JavaFX thread. | Concurrency |
| Aggregate completed item quantities and completed order counts by hour; exclude cancelled orders. | Data Mining |
| Labeled bar chart of item quantities sold and line chart of orders by hour. | Data Visualization |


### 2.3 Team Roles and Responsibilities
| Member | Primary Responsibility |
|---|---|
| Kiro Del Rosario - Team Leader | TheMealDB integration and background CSV-import plan. |
| Jamaica Ambuhot - Developer 1 | OOD hierarchy and recursive combo pricing plan. |
| Janine Ebrado - Developer 2 | ER sketch and SQLite/JDBC data-layer plan. |
| Vincent Hayag - DB Admin | Kitchen Queue, cancellation Stack and stock-flow plan. |
| Josiela Yap - UI (User Interface) | JavaFX screen sketch and event-handler plan. |
| Ben Justin Lajot - QA (Quality Assurance) | JUnit test cases, sales aggregation and chart plan. |




