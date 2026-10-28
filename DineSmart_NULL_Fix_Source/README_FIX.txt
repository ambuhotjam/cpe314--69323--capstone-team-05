# DineSmart NULL/blank menu cleanup fix

This package contains the revised Java source and pom.xml for the supplied DineSmart project.

## Changes
- `MenuItemRepository.insert()` rejects null/blank names and the literal text `NULL`.
- `deleteInvalidRows()` removes menu rows with null/blank/`NULL` names or invalid required fields.
- Menu list and search queries do not display rows with null/blank/`NULL` names.
- `DineSmartApp` runs the cleanup after database initialization on startup and when the Initialize Database button is used.

## Apply in Eclipse
1. Back up your current project folder and `dinesmart.db` first.
2. Extract this ZIP.
3. Copy the extracted `src` folder and `pom.xml` into your existing project, allowing the two revised Java files to replace the old ones.
4. In Eclipse, right-click the project > Maven > Update Project.
5. Run `ph.uc.cpe314.dinesmart.ui.DineSmartApp`.

The ZIP intentionally does not include `dinesmart.db` or generated `target/` files, to avoid replacing your local database or build output. The cleanup only removes invalid menu rows; it does not delete all valid menu items.
