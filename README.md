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

Authentication uses Django session cookies. CSRF tokens are extracted from the cookie jar and injected automatically into mutating requests. The session cookie is persisted, so a valid login survives an app restart (until it expires or you log out).

---

## Features

### Login

- Enter the server base URL (e.g. `http://192.168.1.10:8000`) and credentials.
- **Read-only without login** opens the app in read-only mode — scanning and listing work, creating/editing/deleting does not.
- The server URL is remembered across sessions.
- **Backend supports item images** — toggle switch below the server URL. Enable this when the backend has image support ([PR #2](https://github.com/b4ckspace/lgr/pull/2)). The setting is persisted across restarts. When enabled, camera buttons appear in the New Barcode and Edit Item screens.
- Release builds show the app version and build date in the lower-right corner of the login screen.

---

### Home

Quick-action tiles and tab shortcuts:

| Tile | Action |
|---|---|
| **Details** | Scan a barcode and jump directly to its detail view |
| **Verify** | Start a content-verification scan session |
| **New** | Open the New Barcode form (requires login) |
| **Items** | Jump to the Items tab |
| **Barcodes** | Jump to the Barcodes tab |
| **Persons** | Jump to the Persons tab (requires login) |
| **Loans** | Jump to the Loans tab (requires login) |
| **My Loans** | Jump to the My Loans tab (requires login) |

---

### Search header

The Barcodes, Items and Persons tabs share one search header so they look and behave the same:

- A rounded **search field** (leading magnifier, trailing clear ✕; the Barcodes one also has a scan icon).
- A meta row with a **Filters** toggle on the left (shows the active-filter count, e.g. *Filters · 2*, and a chevron) and the **result count** on the right. Tabs with no filters (Persons) omit the toggle.
- When filters are active, they appear as **removable chips** under the field — visible even while the filter panel is collapsed.
- The **collapsible filter panel** holds that tab's specific filters; it's hidden behind the Filters toggle so every tab has the same compact layout.

---

### Barcodes tab

Searchable, paginated list of all barcodes.

The search area uses the shared search header (see *Search header* below). The barcode search field also has a **scan-to-search** icon (camera) that clears any active filters, then scans an existing barcode and fills it into the field (beeps on found, burps on unknown). The field supports `!user:` and `!item:` syntax (backend-defined): e.g. `!user:john` finds all barcodes currently on loan to the person with nickname *john*, and `!item:eurobox` finds all barcodes whose item is named *eurobox* (both match the name exactly, case-insensitive).

- **Filters** (behind the *Filters* toggle):
  - **No location** — show only barcodes without a parent location.
  - **Owner** — type-ahead person search (min. 2 characters). Tap a person's name to filter by that person only; tap the checkbox to add/remove from a multi-owner selection. Active owners (and the no-location filter) appear as removable chips under the search field. Filters by the barcode's *Owner* field (not loan person).
- **Add new barcode** — a **＋ floating action button** (bottom-right) opens the New Barcode form. Hidden in read-only mode.
- **Infinite scroll** — next pages load automatically as you scroll.
- **Pull to refresh** — pull down on the list to force a reload (respects the active search and filter).
- **Cached on tab switch** — switching away and back reuses the loaded result set and restores the scroll position; a new network request is only made when the search or filter changes.
- **Barcode selection** — check the checkbox on any barcode card to add it to a loan selection. While the selection is non-empty, a shopping-cart icon with a count badge appears in the top bar; tap it to open the Loan Cart.

#### Barcode detail

Tap any barcode in the list (or scan from Home → Details) to open the detail view.

**Displayed fields** (top to bottom — Item, Barcode, Location):
- Item name and description
- Barcode code
- **Item image** — shown below the barcode when the backend supports images and an image has been set. The full image is always shown (never cropped): landscape images fill the width, portrait images are capped in height and centered. Tap to view the image fullscreen; tap the fullscreen image to dismiss it.
- Location (breadcrumb of parent chain, tappable to navigate)
- Per-barcode description (labelled *Barcode description*)
- Owner (resolved to display name)
- Loan status — *Available* (green) or *On loan — Person* (blue)

**Navigation:**
- When opened from the list, arrow buttons and swipe left/right navigate through the result set.
- Tapping a location breadcrumb or a content item navigates into that barcode, maintaining back-navigation history (Android back button / swipe right goes back through the chain).
- **Pull to refresh** — pull down to reload the current barcode's data from the backend.
- **Long-press to copy** — long-press any field row to copy its value to the clipboard. This works on all the detail pages (Barcode, Item, Person and Loan).

**Actions (authenticated):**
- **New** — create another barcode immediately (only shown right after a barcode was just created, to support rapid sequential entry)
- **Edit** — open the Edit Barcode screen (see below)
- **Cart** — add the current barcode to the loan selection and return to the list
- **Delete** — confirmation dialog, then permanently deletes the barcode and returns to the list

**Editing a barcode:**
Tap the Edit icon (pencil) in the top bar to open the edit screen. The loan status is not editable. Fields are ordered Item, Barcode, Location:
- **Item** — type-ahead search; selecting a suggestion fills in the item description. If the typed name does not exist yet, a new item is created on save.
- **Barcode** — the existing code, read-only here (change it via *Changing the barcode code* below).
- **Location** — type-ahead barcode search (min. 2 characters, 300 ms debounce). Suggestions show the item name and barcode code; selecting one sets the parent barcode. Pre-filled with the current parent (if any).
- **Barcode description** — per-barcode description. Multi-line; when the text exceeds the field height, only the text scrolls inside the fixed outline and a thin scrollbar appears on the right; the field keeps the line you are typing on in view (same for the New Barcode and Edit Item description fields).
- **Item description** — editable when no item is selected from suggestions; read-only once an item is chosen.
- **Owner** — type-ahead person search; tap the person icon to set to the current user.

> Item photo editing is not available here — use Edit Item to change a photo.

**Changing the barcode code:**
The code is the barcode's identifier and cannot be edited in place, so changing it is done by recreating the entry under the new code:
- The *Barcode* field is read-only with a **pencil icon** beside it. The pencil is only available as the very first action after opening the screen — once you change any other field it is disabled (so a code change is always done on its own). It is also disabled while the barcode is on loan (return it first).
- Tap the pencil to make the code editable. A **scan icon** lets you scan the new code, and an **× icon** undoes the change and restores the original code.
- As soon as the code actually differs from the original, **all other fields are locked** — you can only **Cancel** or **Save**.
- Tapping **Save** shows a confirmation dialog. On confirm, a new barcode is created under the new code (copying item, owner, location and description), any contained child barcodes are moved to it, and the old entry is deleted. The scan history is not carried over.
- The new code must not already exist.

**Cancel** and **Save** buttons sit at the bottom of the screen (above the keyboard). Tap **Save** to write the changes; tap **Cancel** or the back arrow to discard.

**Location editing:**
- Tap the scan icon next to *Location* to scan a new parent barcode. The new parent chain is shown with newly added ancestors highlighted in green. **Save** and **Cancel** appear at the bottom of the screen.

**Content list:**
- Shows all child barcodes with their item names. Tappable for navigation.
- Loan status shown per child (blue label if on loan).
- Four icon buttons in the *Content* header (authenticated):
  - **Search icon** — shows a text search field inline below the header. Type a barcode code or item name (min. 2 characters, 300 ms debounce); tap any suggestion to add it immediately. Already-present and already-added barcodes are shown in grey and cannot be selected. Tap the search icon again (now shown as ×) to close the field. The field is also closed automatically when Save or Cancel is tapped.
  - **Scan icon** — opens *Add content* scanner; adds newly scanned items to the existing content without removing anything.
  - **New-barcode icon** — opens the New Barcode form with this container pre-filled as the location, to register a brand-new barcode directly inside it.
  - **Verify icon** — opens the content scanner; scan every physical item in the container. While verifying, the *Content* section switches to the same two-column **Current | Scanned** table used by the standalone Verify workflow (expected-but-missing items in red on the left, unexpected scans in green on the right). Tapping the icon again while a verify is already in progress *adds* to the already-scanned list rather than replacing it, and the camera shows *Scan additional content* (a fresh verify shows *Scan all content*). A **Cancel** button (and the back arrow) abort the scan without changing content.
- The search, scan and add modes share the same list of added barcodes and can be combined freely within a session.
- Once any content has been added or scanned, **Save** and **Cancel** buttons appear at the bottom. Cancel discards all changes; Save writes them to the backend. Children that are out on loan are never detached, even when they were not re-scanned during a verify. The scan state resets when navigating to a different barcode.

---

### Items tab

Searchable, paginated list of item types (the catalogue, not individual barcoded instances).

- Text search by name (shared search header — see *Search header* above).
- **No barcodes filter** — behind the *Filters* toggle; shows only item types that have no barcodes registered yet.
- Displays name, description, and tag count per item.
- Infinite scroll.
- **Pull to refresh** — pull down on the list to force a reload (respects the active search and filter).
- **Cached on tab switch** — the result set, active search string and scroll position are preserved when switching away and back.
- Tap any item to open its **Item Detail** page.
- **Arrow buttons and swipe left/right** navigate through the result set (same as Barcodes). Each item's barcodes are cached once loaded, so swiping back to an already-visited item shows them instantly without re-fetching (pull to refresh forces a reload).

---

### Item Detail

Reachable by tapping an item in the Items tab, or by tapping the item name in a Barcode Detail view (shown in primary colour as a link).

**Displayed fields:**
- Item name (labelled *Item*)
- **Item image** — shown below the name when the backend supports images and an image has been set. The full image is always shown (never cropped). Tap to view fullscreen (pinch-to-zoom, double-tap to toggle 2×, tap to close when not zoomed).
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

- Text search by name/nickname (shared search header — see *Search header* above; no filters, so no Filters toggle).
- Displays full name (primary), nickname (if different), and email per person.
- **Add new person** — a person-add **floating action button** (bottom-right) opens the New Person screen. Hidden in read-only mode.
- Infinite scroll.
- **Pull to refresh** — pull down to force a reload.
- **Cached on tab switch** — the result set, active search string and scroll position are preserved when switching away and back.
- Tap any person to open its **Person Detail** page.
- **Arrow buttons and swipe left/right** navigate through the result set (same as Items and Barcodes).
- The tab is disabled when not logged in.

---

### Person Detail

Reachable by tapping a person in the Persons tab.

**Displayed fields:** Nickname, First name, Last name, Email (each shown only when set; nickname is always shown). Long-press a field to copy its value (see *Barcode detail* for this shared behaviour).

- **Pull to refresh** — pull down to reload the person from the backend.
- **Arrow buttons and swipe left/right** navigate through the result set (same as Items and Barcodes).
- The footer keeps the **Persons** tab highlighted, and tapping the Persons icon from another tab returns to this Person Detail page (same as the other tabs' detail pages).

**Actions (authenticated, hidden in read-only mode):**
- **Edit** (pencil icon) — opens the Edit Person screen. Editable fields: Nickname (required), First name, Last name, Email. **Cancel** and **Save** buttons are at the bottom of the screen.
- **Delete** (trashcan icon) — opens a confirmation dialog and permanently deletes the person.

---

### New Person

Reachable via the person-add floating action button in the Persons tab. Same form as Edit Person: Nickname (required), First name, Last name, Email, with **Cancel** and **Save** at the bottom. On success, the new person's **Person Detail** page opens.

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
- Above the table the screen shows the location's details using the same rows, order and colours as the Barcode Detail page — **Item**, **Barcode**, **Location**, optional **Barcode description**, **Item description**, **Owner** and **Loan**, all in the normal text colour. The **Item** row is still tappable (navigates to Item Detail) but no longer highlighted. The breadcrumb ancestors remain tappable to their Barcode Detail.
- A verify icon in the *Content* header re-opens the scanner in **Scan additional content** mode: scanned barcodes are *added* to the already-scanned list. It can be used repeatedly, each time appending to the list. The scanned list is only reset when you leave the result screen (back arrow, **Cancel**, or **Save**).
- **Pull to refresh** — pull down on the result screen to reload the location's current data from the backend; the list of already-scanned items in the current session is preserved.
- The list auto-scrolls so the *Content* header is the first visible item when the screen opens.
- The result screen highlights the **Barcodes** tab; while the verify is still in progress (not yet saved or discarded), switching to another tab and back to **Barcodes** returns to the verify result. Once the verify is left/cleared, the Barcodes tab shows the normal list instead.

**If there are mismatches:**
- **Cancel** — discards changes and navigates to the location barcode's detail page.
- **Save** — writes the scanned reality to the backend (updates parent references), then navigates to the location barcode's detail page. Children that are currently out on loan are kept attached even if they were not physically scanned (same guard as the in-place content verify).

**If everything matches:**
- **Verify next** — resets the scan state and returns to the location scanner to verify another container.
- **OK** — navigates to the location barcode's detail page.

---

### New Barcode

Form to register a new barcode in the system.

Fields are ordered Item, Barcode, Location:

| Field | Notes |
|---|---|
| **Item** * | Type-ahead search against the item catalogue (min. 2 characters, 300 ms debounce). Selecting a suggestion fills in the item description. If the name does not exist yet, a new item is created on save. |
| **Barcode** * | The barcode string. Enter manually, scan (camera icon), or tap **+1** to auto-generate the next available numeric code. While the +1 search is running a spinner is shown and the field is read-only; it becomes editable again once a free code is found. Only accepts codes not already in the system (burp if already known). |
| **Location** | Optional parent barcode. Type-ahead barcode search (min. 2 characters, 300 ms debounce) — suggestions show item name and code. Or tap the scan icon to scan a barcode. |
| **Description** | Free-text description for this specific barcode instance. |
| **Item description** | Pre-filled from the selected item (read-only). Editable when no item is selected. |
| **Owner** | Optional. Type-ahead person search or tap the person icon to assign yourself. |
| **Photo** | (When backend supports images) Tap the camera icon to take a photo for the item. A thumbnail preview is shown; tap the × to remove it before saving. Disabled when an existing item is selected from suggestions (use Edit Item to change that item's photo). |

**Cancel** and **Save** buttons sit at the bottom of the screen (above the keyboard when it is open). Tap **Save** to create; on success, the detail view of the new barcode opens immediately. If the server returns an error, the screen scrolls back to the top to show it.

---

### Loans tab

Paginated list of all loans (requires login). Tap any loan card to open the **Loan Detail** screen.

**Loan card shows:** Loan ID, status badge (TAKEN / RETURNED), description, a preview of the first few barcodes (item name with the code in grey, matching the Barcode Detail contents formatting; "+N more" if there are more), taken date, due date, returned date.

- **Result count** — a row below the status filter shows the number of loans matching the current filter.
- **Pull to refresh** — pull down on the list to force a reload.
- **Cached on tab switch** — switching away and back reuses the loaded result set and restores the scroll position; a new network request is only made when the status filter changes, on pull to refresh, or after a loan is created or returned.
- **Arrow buttons and swipe left/right** navigate through the result set in Loan Detail (same as Barcodes and Items).

### My Loans tab

Paginated list of the current user's loans (requires login). Same UI as Loans.

- **Result count** — a row below the status filter shows the number of loans matching the current filter.
- **Pull to refresh** — pull down on the list to force a reload.
- **Cached on tab switch** — same caching behaviour as the Loans tab.
- **Arrow buttons and swipe left/right** navigate through the result set in Loan Detail.

### Loan Detail

Uses the same look-and-feel as the Barcode Detail page: a compact header, labelled field rows (long-press a field to copy its value), and a fixed prev/next navigation row at the bottom.

Shows all fields for a loan:
- Status (coloured red for TAKEN, green for RETURNED), person, description, taken/return/returned dates — each as a labelled row. *Return by* turns red when overdue.
- Full list of barcodes (labelled *Barcodes (N)*) formatted as *item name (code)* with the code in grey, like the Barcode Detail contents — each is a tappable link to its Barcode Detail screen (long-press to copy the code). Resolved item names are cached, so revisiting a loan or swiping between loans shows them instantly without re-fetching.
- **Arrow buttons** (and swipe left/right) to navigate to the previous/next loan in the list, pinned to the bottom of the screen.
- **Return loan** button — visible when status is TAKEN and the loan belongs to the current user (regardless of whether it was opened from Loans or My Loans). Opens a confirmation dialog; on confirm, marks the loan as returned and refreshes both loan lists.
- The footer keeps the tab the loan was opened from highlighted — **My Loans** when opened from My Loans, otherwise **Loans**.
- A loan detail can be open on the Loans tab and the My Loans tab at the same time; tapping the footer icons switches directly between the two open loan details (and to a tab's list when it has no loan open).

### Loan Cart

When one or more barcodes have been added to the loan selection (via the shopping cart icon in Barcode Detail or via the checkbox in the Barcodes tab), a **🛒 N** badge appears in the top bar on all non-camera screens. Tapping it opens the Loan Cart screen.

**Creating a loan:**
1. Add barcodes to the cart using the cart icon in Barcode Detail.
2. Tap the cart badge in the top bar.
3. Review the barcode list (each entry is a tappable link to its Barcode Detail). Remove items with ×.
4. Optionally enter a **Description** for the loan (multi-line).
5. Optionally set a **Return date** via the calendar picker or by typing `YYYY-MM-DD`.
6. **Preview** — shows which barcodes are available (green) and which are blocked (red, already on loan). Each entry is a tappable link to its Barcode Detail.
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

Switching to a different tab and back restores where you were on that tab (the open detail/sub-screen and scroll position). Tapping the tab you are **already** on resets it — it returns to that tab's list and closes any open detail/sub-screen.

Creating new entries is done from each tab's **floating action button** (Add new barcode on the Barcodes tab, Add new person on the Persons tab), hidden in read-only mode. The top bar shows the loan-cart badge when the loan selection is non-empty.

---

## License

Copyright 2026 Andreas Uhsemann. Licensed under the [Apache License, Version 2.0](LICENSE).

### Third-party open-source licenses

The app bundles a number of open-source components (AndroidX/Jetpack Compose, CameraX,
Retrofit/OkHttp, Coil, Google ML Kit, and others). Their licenses are listed in-app on an
**Open-source licenses** screen, reachable from the bottom-left link on the login screen. The
list is generated at build time by the [AboutLibraries](https://github.com/mikepenz/AboutLibraries)
Gradle plugin from the project's dependency metadata, so it stays in sync as dependencies change.
Google ML Kit additionally carries Google's ML Kit terms and bundles its own third-party
components, which are noted on that screen.
