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
| Image loading | Coil 2 |
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
- **Backend supports item images** — toggle switch below the server URL. Enable this when the backend has image support (PR #2). The setting is persisted across restarts. When enabled, camera buttons appear in the New Barcode, Edit Barcode, and Edit Item screens.
- Release builds show the app version and build date in the lower-right corner of the login screen.

---

### Home

Quick-action tiles and tab shortcuts:

| Tile | Action |
|---|---|
| **Details** | Scan a barcode and jump directly to its detail view |
| **Verify** | Start a content-verification scan session |
| **New** | Open the New Barcode form (authenticated users only) |
| **Items** | Jump to the Items tab |
| **Barcodes** | Jump to the Barcodes tab |
| **Persons** | Jump to the Persons tab (requires login) |
| **Loans** | Jump to the Loans tab (requires login) |
| **My Loans** | Jump to the My Loans tab (requires login) |

---

### Barcodes tab

Searchable, paginated list of all barcodes.

- **Text search** — searches code, item name, description. Supports `!user:` and `!item:` syntax (backend-defined).
- **Scan to search** — tap the camera icon in the search field; scans an existing barcode and fills it into the search field automatically. Beeps on found, burps on unknown.
- **Result count** and **filter toggle** — a row below the search field shows the result count (right) and an expand/collapse icon (left) to show/hide additional filters. The state is remembered across app restarts.
- **Additional filters** (collapsible, hidden by default):
  - **No location** — filter chip to show only barcodes without a parent location.
  - **Owner** — type-ahead person search (min. 2 characters). Tap a person's name to filter by that person only; tap the checkbox to add/remove from a multi-owner selection. Selected owners appear as removable chips. Filters by the barcode's *Owner* field (not loan person).
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
- **Item image** — shown below the item name when the backend supports images and an image has been set. Tap to view the image fullscreen; tap the fullscreen image to dismiss it.
- Per-barcode description (labelled *Barcode description*)
- Owner (resolved to display name)
- Loan status — *Available* (green) or *On loan — Person* (blue)

**Navigation:**
- When opened from the list, arrow buttons and swipe left/right navigate through the result set.
- Tapping a location breadcrumb or a content item navigates into that barcode, maintaining back-navigation history (Android back button / swipe right goes back through the chain).
- **Pull to refresh** — pull down to reload the current barcode's data from the backend.

**Actions (authenticated):**
- **New** — create another barcode immediately (only shown right after a barcode was just created, to support rapid sequential entry)
- **Edit** — open the Edit Barcode screen (see below)
- **Cart** — add the current barcode to the loan selection and return to the list
- **Delete** — confirmation dialog, then permanently deletes the barcode and returns to the list

**Editing a barcode:**
Tap the Edit icon (pencil) in the top bar to open the edit screen. The barcode code is shown read-only and cannot be changed. The loan status is not editable. Editable fields:
- **Location** — type-ahead barcode search (min. 2 characters, 300 ms debounce). Suggestions show the item name and barcode code; selecting one sets the parent barcode. Pre-filled with the current parent (if any).
- **Item** — type-ahead search; selecting a suggestion fills in the item description. If the typed name does not exist yet, a new item is created on save.
- **Barcode description** — per-barcode description.
- **Item description** — editable when no item is selected from suggestions; read-only once an item is chosen.
- **Owner** — type-ahead person search; tap the person icon to set to the current user.

> Item photo editing is not available here — use Edit Item to change a photo.

**Cancel** and **Save** buttons sit at the bottom of the screen (above the keyboard). Tap **Save** to write the changes; tap **Cancel** or the back arrow to discard.

**Location editing:**
- Tap the scan icon next to *Location* to scan a new parent barcode. The new parent chain is shown with newly added ancestors highlighted in green. **Save** and **Cancel** appear at the bottom of the screen.

**Content list:**
- Shows all child barcodes with their item names. Tappable for navigation.
- Loan status shown per child (blue label if on loan).
- Three icon buttons in the *Content* header (authenticated):
  - **Verify icon** — opens *Scan all content* scanner; scan every physical item in the container. Items in the database but not yet scanned are shown in red; newly scanned items in green. A **Cancel** button (and the back arrow) abort the scan without changing content.
  - **Scan icon** — opens *Add content* scanner; adds newly scanned items to the existing content without removing anything.
  - **Search icon** — shows a text search field inline below the header. Type a barcode code or item name (min. 2 characters, 300 ms debounce); tap any suggestion to add it immediately. Already-present and already-added barcodes are shown in grey and cannot be selected. Tap the search icon again (now shown as ×) to close the field. The field is also closed automatically when Save or Cancel is tapped.
- All three add modes share the same list of added barcodes and can be combined freely within a session.
- Once any content has been added or scanned, **Save** and **Cancel** buttons appear at the bottom. Cancel discards all changes; Save writes them to the backend. The scan state resets when navigating to a different barcode.

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
- Item name (labelled *Item*)
- **Item image** — shown below the name when the backend supports images and an image has been set. Tap to view fullscreen (pinch-to-zoom, double-tap to toggle 2×, tap to close when not zoomed).
- Item description (labelled *Item description*, shown only if set)
- **Barcodes** section — all barcodes that use this item type, each tappable to open its Barcode Detail

**Pull to refresh** — pull down to reload the item's linked barcodes.

**Actions (authenticated):**
- **Edit** (pencil icon) — opens the Edit Item screen. Editable fields: Item (name), Item description, and (when backend supports images) Photo. The current item image is shown; tap the camera icon to take a new photo, or tap the trashcan to delete the current photo (tap undo to cancel). **Cancel** and **Save** buttons are at the bottom of the screen. The item list is refreshed after saving.
- **Delete** (trashcan icon) — opens a confirmation dialog and permanently deletes the item.
  - The trashcan is disabled (greyed out) if any barcodes are linked to the item.

---

### Persons tab

Searchable, paginated list of persons (requires login).

- Text search by name/nickname.
- Displays full name (primary), nickname (if different), and email per person.
- Infinite scroll.
- **Pull to refresh** — pull down to force a reload.
- **Cached on tab switch** — the result set and active search string are preserved when switching away and back.
- The tab is disabled when not logged in.

---

### Verify workflow

Used to audit whether the physical contents of a container match the database.

1. **Home → Verify** opens the location scanner.
2. Scan the container/location barcode. The scanner switches to content mode.
3. Scan each item found inside the container:
   - New item (not yet in DB for this location): short beep, scanner border flashes green.
   - Already scanned (duplicate): triple beep.
   - Unknown barcode: burp.
4. Tap **Done** to open the verification result screen, or **Cancel** (or the back arrow) to abort and return to the Home screen.

The result screen shows a two-column table:

| Current (in DB) | Scanned |
|---|---|
| Items the DB thinks are here | Items physically found |

- Items in both columns (matched) are shown normally.
- Items only in *Current* (missing physically) are shown in red.
- Items only in *Scanned* (unexpected extras) are shown in green.
- Tapping any barcode entry in either column navigates to its Barcode Detail. The Android back button returns to the verify result.
- The location breadcrumb ancestors and the **Item** row are tappable links (shown in primary colour) — tapping navigates to the respective Barcode Detail or Item Detail.
- A re-scan icon in the *Content* header lets you re-run the content scan.
- **Pull to refresh** — pull down on the result screen to reload the location's current data from the backend; the list of already-scanned items in the current session is preserved.
- The list auto-scrolls so the *Content* header is the first visible item when the screen opens.
- The result screen highlights the **Barcodes** tab.

**If there are mismatches:**
- **Cancel** — discards changes and navigates to the location barcode's detail page.
- **Save** — writes the scanned reality to the backend (updates parent references), then navigates to the location barcode's detail page.

**If everything matches:**
- **Verify next** — resets the scan state and returns to the location scanner to verify another container.
- **OK** — navigates to the location barcode's detail page.

---

### New Barcode

Form to register a new barcode in the system.

| Field | Notes |
|---|---|
| **Location** | Optional parent barcode. Type-ahead barcode search (min. 2 characters, 300 ms debounce) — suggestions show item name and code. Or tap the scan icon to scan a barcode. |
| **Barcode** * | The barcode string. Enter manually, scan (camera icon), or tap **+1** to auto-generate the next available numeric code. While the +1 search is running a spinner is shown and the field is read-only; it becomes editable again once a free code is found. Only accepts codes not already in the system (burp if already known). |
| **Item** * | Type-ahead search against the item catalogue (min. 2 characters, 300 ms debounce). Selecting a suggestion fills in the item description. If the name does not exist yet, a new item is created on save. |
| **Description** | Free-text description for this specific barcode instance. |
| **Item description** | Pre-filled from the selected item (read-only). Editable when no item is selected. |
| **Owner** | Optional. Type-ahead person search or tap the person icon to assign yourself. |
| **Photo** | (When backend supports images) Tap the camera icon to take a photo for the item. A thumbnail preview is shown; tap the × to remove it before saving. Disabled when an existing item is selected from suggestions (use Edit Item to change that item's photo). |

**Cancel** and **Save** buttons sit at the bottom of the screen (above the keyboard when it is open). Tap **Save** to create; on success, the detail view of the new barcode opens immediately. If the server returns an error, the screen scrolls back to the top to show it.

---

### Loans tab

Paginated list of all loans (requires login). Tap any loan card to open the **Loan Detail** screen.

**Loan card shows:** Loan ID, status badge (TAKEN / RETURNED), description, barcode codes preview, taken date, due date, returned date.

- **Pull to refresh** — pull down on the list to force a reload.
- **Arrow buttons and swipe left/right** navigate through the result set in Loan Detail (same as Barcodes and Items).

### My Loans tab

Paginated list of the current user's loans (requires login). Same UI as Loans.

- **Pull to refresh** — pull down on the list to force a reload.
- **Arrow buttons and swipe left/right** navigate through the result set in Loan Detail.

### Loan Detail

Shows all fields for a loan:
- Status badge, person, description, taken/return/returned dates
- Full list of barcodes — each is a tappable link to its Barcode Detail screen
- **Arrow buttons** (and swipe left/right) to navigate to the previous/next loan in the list, pinned to the bottom of the screen.
- **Return loan** button — visible when status is TAKEN and the loan belongs to the current user (regardless of whether it was opened from Loans or My Loans). Opens a confirmation dialog; on confirm, marks the loan as returned and refreshes both loan lists.

### Loan Cart

When one or more barcodes have been added to the loan selection (via the shopping cart icon in Barcode Detail), a **🛒 N** badge appears in the top bar on all non-camera screens. Tapping it opens the Loan Cart screen.

**Creating a loan:**
1. Add barcodes to the cart using the cart icon in Barcode Detail.
2. Tap the cart badge in the top bar.
3. Review the barcode list (each entry is a tappable link to its Barcode Detail). Remove items with ×.
4. Optionally enter a **Description** for the loan.
5. Optionally set a **Return date** via the calendar picker or by typing `YYYY-MM-DD`.
6. **Preview** — shows which barcodes are available (green) and which are blocked (red, already on loan to someone else).
7. **Confirm** — creates the loan with the available barcodes. On success, the cart is cleared and you are navigated to the My Loans tab.

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

## Navigation

A compact icon-only bottom bar is always visible (except on camera/scanner screens). The top bar title reflects the active tab name (Home, Items, Barcodes, Persons, Loans, My Loans). On sub-pages (detail, edit, scan) the tab icon corresponding to the parent tab stays subtly highlighted.

---

## Known Limitations

- No offline support — all data is fetched live from the backend.
- Session is not persisted across app restarts; you must log in again after closing the app.
- The server URL must be reachable directly (no OAuth / proxy support).

---

## License

Copyright 2026 Andreas Uhsemann. Licensed under the [Apache License, Version 2.0](LICENSE).
