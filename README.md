# LGR Android

Android client for the **LGR** warehouse management system. Connects to a Django REST Framework backend to browse, scan, and manage barcoded inventory items, locations, and loans.

## Requirements

- Android 8.0 (API 26) or newer
- A running [lgr](https://github.com/b4ckspace/lgr) Django backend instance reachable over the network
- Camera permission (for barcode scanning)

## Building

### Prerequisites

- Android Studio or the Android SDK command-line tools
- JDK 17 (e.g. from `~/android-dev/`)
- `JAVA_HOME` pointing to your JDK

### Debug build

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Release build

Create `keystore.properties` in the project root (this file is gitignored):

```properties
storeFile=../lgr-release.jks
storePassword=<password>
keyAlias=lgr
keyPassword=<password>
```

Then build and install:

```bash
./gradlew assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

> If switching from a debug to a release APK (or vice versa), uninstall the existing app first: `adb uninstall de.backspace.lgr`

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| Networking | Retrofit 2 + OkHttp 4 |
| JSON | Gson |
| Camera | CameraX |
| Barcode scanning | ML Kit Barcode Scanning |
| Architecture | Single-ViewModel (AppViewModel) |

## Architecture

```
app/
├── data/
│   ├── api/          ApiService (Retrofit), ApiClient (OkHttp + CSRF)
│   ├── model/        Data classes (Barcode, Item, Person, Loan, …)
│   └── repository/   LgrRepository — all network calls
├── viewmodel/        AppViewModel — single source of truth for UI state
└── ui/
    ├── navigation/   AppNavigation — nav graph + scaffold
    ├── screens/      One file per screen/flow
    └── theme/        Material 3 theme
```

Authentication uses Django session cookies. CSRF tokens are extracted from the cookie jar and injected automatically into mutating requests.

---

## Features

### Login

- Enter the server base URL (e.g. `http://192.168.1.10:8000`) and credentials.
- **Read-only without login** opens the app in read-only mode — scanning and listing work, creating/editing/deleting does not.
- The server URL is remembered across sessions.
- Release builds show the app version and build date in the lower-right corner of the login screen.

---

### Home

Three quick-action tiles:

| Tile | Action |
|---|---|
| **Details** | Scan a barcode and jump directly to its detail view |
| **Verify** | Start a content-verification scan session |
| **New** | Open the New Barcode form (authenticated users only) |

---

### Barcodes tab

Searchable, paginated list of all barcodes.

- **Text search** — searches code, item name, description. Supports `!user:` and `!item:` syntax (backend-defined).
- **Scan to search** — tap the camera icon in the search field; scans an existing barcode and fills it into the search field automatically. Beeps on found, burps on unknown.
- **No location filter** — filter chip to show only barcodes without a parent location.
- **Result count** shown in the filter bar ("1 result" / "N results").
- **Infinite scroll** — next pages load automatically as you scroll.
- **Pull to refresh** — pull down on the list to force a reload (respects the active search and filter).
- **Cached on tab switch** — switching away and back reuses the loaded result set; a new network request is only made when the search or filter changes.
- **Barcode selection** — check the checkbox on any barcode card to add it to a loan selection. A banner shows the current count; the cart FAB appears when items are selected.

#### Barcode detail

Tap any barcode in the list (or scan from Home → Details) to open the detail view.

**Displayed fields:**
- Location (breadcrumb of parent chain, tappable to navigate)
- Barcode code
- Item name and description
- Per-barcode description
- Owner (resolved to display name)
- Loan status — *Available* (green) or *On loan — Person* (blue)

**Navigation:**
- When opened from the list, arrow buttons and swipe left/right navigate through the result set.
- Tapping a location breadcrumb or a content item navigates into that barcode, maintaining back-navigation history (Android back button / swipe right goes back through the chain).

**Actions (authenticated):**
- **New** — create a new barcode (same as Home → New)
- **Edit** — open the Edit Barcode screen (see below)
- **Cart** — add the current barcode to the loan selection and return to the list
- **Delete** — confirmation dialog, then permanently deletes the barcode and returns to the list

**Editing a barcode:**
Tap the Edit icon (pencil) in the top bar to open the edit screen. The barcode code is shown read-only and cannot be changed. The loan status is not editable. Editable fields:
- **Item** — type-ahead search; selecting a suggestion fills in the item description. If the typed name does not exist yet, a new item is created on save.
- **Description** — per-barcode description.
- **Item description** — editable when no item is selected from suggestions; read-only once an item is chosen.
- **Owner** — type-ahead person search; tap the person icon to set to the current user.

Tap **Save** to write the changes; tap **Cancel** or the back arrow to discard.

**Location editing:**
- Tap the scan icon next to *Location* to scan a new parent barcode. The new parent chain is shown with newly added ancestors highlighted in green. Save or Cancel.

**Content list:**
- Shows all child barcodes with their item names. Tappable for navigation.
- Loan status shown per child (blue label if on loan).
- Two icon buttons in the *Content* header (authenticated):
  - **Verify icon** — opens *Scan all content* scanner; scan every physical item in the container. Items in the database but not yet scanned are shown in red; newly scanned items in green.
  - **Scan icon** — opens *Add content* scanner; adds newly scanned items to the existing content without removing anything.
- Both scan modes share the same list of scanned barcodes. Switching between them mid-session preserves everything already scanned. Closing the scanner and reopening it also continues from where you left off.
- Once scanning has started, **Save** and **Cancel** buttons appear at the bottom. Cancel discards all scan progress; Save writes the changes to the backend. The scan state resets when navigating to a different barcode.

---

### Items tab

Searchable, paginated list of item types (the catalogue, not individual barcoded instances).

- Text search by name.
- **No barcodes filter** — shows only item types that have no barcodes registered yet.
- Displays name, description, and tag count per item.
- Infinite scroll.
- **Pull to refresh** — pull down on the list to force a reload (respects the active search and filter).
- **Cached on tab switch** — the result set and active search string are preserved when switching away and back.
- Tap any item to open its **Item Detail** page.
- **Arrow buttons and swipe left/right** navigate through the result set (same as Barcodes).

---

### Item Detail

Reachable by tapping an item in the Items tab, or by tapping the item name in a Barcode Detail view (shown in primary colour as a link).

**Displayed fields:**
- Item name
- Item description (if set)
- **Barcodes** section — all barcodes that use this item type, each tappable to open its Barcode Detail

**Actions (authenticated):**
- **Edit** (pencil icon) — opens the Edit Item screen. Editable fields: Name and Description. Save/Cancel buttons in the top bar. The item list is refreshed after saving.
- **Delete** (trashcan icon) — opens a confirmation dialog and permanently deletes the item.
  - The trashcan is disabled (greyed out) if any barcodes are linked to the item.

---

### Verify workflow

Used to audit whether the physical contents of a container match the database.

1. **Home → Verify** opens the location scanner.
2. Scan the container/location barcode. The scanner switches to content mode.
3. Scan each item found inside the container:
   - New item (not yet in DB for this location): short beep, scanner border flashes green.
   - Already scanned (duplicate): triple beep.
   - Unknown barcode: burp.
4. Tap **Done** to open the verification result screen.

The result screen shows a two-column table:

| Current (in DB) | Scanned |
|---|---|
| Items the DB thinks are here | Items physically found |

- Items in both columns (matched) are shown normally.
- Items only in *Current* (missing physically) are shown in red.
- Items only in *Scanned* (unexpected extras) are shown in green.

If there are mismatches, a **Save** button writes the scanned reality to the backend (updates parent references of all scanned barcodes).

---

### New Barcode

Form to register a new barcode in the system.

| Field | Notes |
|---|---|
| **Location** | Optional parent barcode. Enter manually or tap the scan icon. |
| **Barcode** * | The barcode string. Enter manually or scan. Only accepts codes not already in the system (burp if already known). |
| **Item** * | Type-ahead search against the item catalogue (min. 2 characters, 300 ms debounce). Selecting a suggestion fills in the item description. If the name does not exist yet, a new item is created on save. |
| **Description** | Free-text description for this specific barcode instance. |
| **Item description** | Pre-filled from the selected item (read-only). Editable when no item is selected. |
| **Owner** | Optional. Type-ahead person search or tap the person icon to assign yourself. |

Tap **Save** in the top bar to create. On success, the detail view of the new barcode opens immediately.

---

### Loans (work in progress)

The Loans, My Loans, and Persons tabs are present in the navigation bar but not yet implemented. Loan creation via the barcode selection + cart flow is functional.

**Creating a loan:**
1. Select one or more barcodes in the Barcodes tab (or via the cart icon in the detail view).
2. Tap the cart FAB.
3. Optionally enter a return date (`YYYY-MM-DD`).
4. **Preview** — shows which barcodes are available and which are blocked (already on loan to someone else).
5. **Confirm** — creates the loan.

---

## Barcode Scan Sound Conventions

| Sound | Meaning |
|---|---|
| Short beep | Success / item found |
| Burp (NACK) | Not found / already known / error |
| Double beep | Already scanned in this session |
| Rising tone | Invalid action (e.g. scanning the location barcode as content) |

---

## Read-only Mode

When launched via *Read-only without login*, the app operates in read-only mode:

- All scanning and listing features are available.
- The New, Edit, Delete, and Save actions are hidden.
- The top bar shows a **Login** button to switch to authenticated mode without restarting.

---

## Known Limitations

- The Persons, Loans, and My Loans tabs are placeholders (display nothing).
- No offline support — all data is fetched live from the backend.
- Session is not persisted across app restarts; you must log in again after closing the app.
- The server URL must be reachable directly (no OAuth / proxy support).

---

## License

Copyright 2026 Andreas Uhsemann. Licensed under the [Apache License, Version 2.0](LICENSE).
