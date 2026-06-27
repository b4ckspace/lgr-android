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
- The login screen shows the app version and build date in its lower-right corner. Release builds show `Version: <n>` and the build date; debug builds read `Debug` and add the build time (e.g. `Debug (2026-06-24 22:05)`).

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
- **Fullscreen mode** — tap the fullscreen icon (⛶) in the top bar to hide the search header along with the app top bar and bottom navigation, so the list spans the whole screen. The Android status and navigation bars (clock, network, battery, nav buttons) are hidden too, so nothing overlaps the content; swipe from an edge to reveal them briefly. Leave fullscreen with the floating exit button (bottom-right) or the back button. Fullscreen is a single app-wide setting shared with the Barcode Detail page: once on, it stays on as you drill into a barcode and back, until you turn it off. Available on every list tab, including Loans and My Loans (which hide their status-filter header the same way).

---

### Barcodes tab

Searchable, paginated list of all barcodes.

The search area uses the shared search header (see *Search header* above). The barcode search field also has a **scan-to-search** icon (camera) that clears any active filters, then scans an existing barcode and fills it into the field (beeps on found, burps on unknown). The field supports `!user:` and `!item:` syntax (backend-defined): e.g. `!user:john` finds all barcodes currently on loan to the person with nickname *john*, and `!item:eurobox` finds all barcodes whose item is named *eurobox* (both match the name exactly, case-insensitive).

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

**Displayed fields:**
- Item name and description
- Barcode code
- **Item image** — shown below the barcode when the backend supports images and an image has been set. The full image is always shown (never cropped): landscape images fill the width, portrait images are capped in height and centered. Tap to view the image fullscreen; tap the fullscreen image to dismiss it.
- Location (breadcrumb of parent chain, tappable to navigate)
- Per-barcode description (labelled *Barcode description*)
- Owner (resolved to display name)
- Loan status — *Available* (green) or *On loan — Person* (blue; the person is resolved to their display name). When on loan, the *Loan* row is tappable (when logged in) and opens that loan's **Loan Detail** page.

**Navigation:**
- When opened from the list, arrow buttons and swipe left/right navigate through the result set.
- Tapping a location breadcrumb or a content item navigates into that barcode, maintaining a navigation history: the Android back button (or swipe right) steps **back** through the chain, and after stepping back, **swipe left** steps **forward** again.
- **Pull to refresh** — pull down to reload the current barcode's data from the backend.
- **Fullscreen** — uses the same single, app-wide fullscreen toggle as the list tabs (turn it on with the ⛶ icon in the header). The header and the bottom navigation are hidden so the details span the whole screen; leave it with the floating exit button (bottom-right) or the back button. Because the setting is shared, opening a barcode while a list is already in fullscreen shows the detail in fullscreen too.
- **Long-press to copy** — long-press any field row to copy its value to the clipboard. This works on all the detail pages (Barcode, Item, Person and Loan).

**Actions (authenticated):**
- **New** — create another barcode immediately (only shown right after a barcode was just created, to support rapid sequential entry)
- **Edit** — open the Edit Barcode screen (see below)
- **Cart** — tap the cart icon to add the current barcode to the loan selection, or tap again to remove it; a count badge shows the selection size. The icon is disabled while the barcode is itself on loan. **Long-press** it for an *express loan* — this replaces the selection with just this barcode and opens the Loan Cart directly.
- **Delete** — confirmation dialog, then permanently deletes the barcode and returns to the previous screen — the previous barcode in the chain if you reached it by following a link, otherwise the list

**Editing a barcode:**
Tap the Edit icon (pencil) in the top bar to open the edit screen. The loan status is not editable.
- **Item** — type-ahead search; selecting a suggestion fills in the item description. Typing the exact name of an existing item selects it automatically, without tapping the suggestion. If the typed name does not exist yet, a new item is created on save.
- **Barcode** — the existing code, read-only here (change it via *Changing the barcode code* below).
- **Location** — type-ahead barcode search (min. 2 characters, 300 ms debounce). Suggestions show the item name and barcode code; selecting one sets the parent barcode. Pre-filled with the current parent (if any).
- **Barcode description** — per-barcode description.
- **Item description** — editable when no item is selected (and a new item is created on save); read-only once an item is chosen, whether by tapping a suggestion or by typing an existing item's exact name.
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
- Icon buttons in the *Content* header (authenticated); an **Adjust** icon also appears while a verify is in progress. The **Verify** and **Adjust** icons show whether their mode is active: idle they are outlined in the normal green, active they show the filled icon variant on a faint green pill, so the active mode(s) are recognisable at a glance. The **Search**, **Scan** and **New-barcode** icons are plain one-shot actions (always outlined, no pill). **While verify mode is active the Search, Scan and New-barcode icons are disabled** (greyed out) — a verify only confirms or removes existing content; use the Verify icon to scan more.
  - **Search icon** — shows a text search field inline below the header. Type a barcode code or item name (min. 2 characters, 300 ms debounce); tap any suggestion to add it immediately. Barcodes that cannot be added are shown in grey and cannot be selected: the container itself, barcodes already in its content, barcodes already added in this session, and any ancestor in the location breadcrumb (so a barcode can never be made a child of its own descendant). Tap the search icon again (now shown as ×) to close the field. The field is also closed automatically when Save or Cancel is tapped.
  - **Scan icon** — opens *Add content* scanner; adds newly scanned items to the existing content without removing anything.
  - **New-barcode icon** — opens the New Barcode form with this container pre-filled as the location, to register a brand-new barcode directly inside it.
  - **Verify icon** — opens the content scanner; scan every physical item in the container. While verifying, the *Content* section switches to the two-column **Current | Scanned** table (expected-but-missing items in red on the left, unexpected scans in green on the right). Tapping the icon again while a verify is already in progress *adds* to the already-scanned list rather than replacing it. A **Cancel** button (and the back arrow) abort the scan without changing content. This is the same verify mode reached via **Home → Verify**. Once the scanned content matches the database, the bottom of the page offers **Verify next** / **OK** (see *Verify workflow*).
  - **Adjust icon** (checklist, verify only) — toggles *adjust mode*, in which every content row gains a checkbox letting you override its default fate on Save: keep a missing (red) item, drop a matched (black) one, or skip a new (green) scan. A checked row stays in / is added to the container; an unchecked row is struck through and will be removed / not added. Defaults follow the colours (matched and new checked, missing unchecked). On-loan children are always kept and their checkbox is disabled. Choices stay visible (as strike-through) after leaving adjust mode and are applied when you Save.
- The search, scan and add modes share the same list of added barcodes and can be combined freely within a session.
- Once any content has been added or scanned, **Save** and **Cancel** buttons appear at the bottom. Cancel discards all changes; Save writes them to the backend. Children that are out on loan are never detached, even when they were not re-scanned during a verify. The scan state resets when navigating to a different barcode.

---

### Items tab

Searchable, paginated list of item types (the catalogue, not individual barcoded instances).

- Text search by name (shared search header — see *Search header* above).
- **No barcodes filter** — behind the *Filters* toggle; shows only item types that have no barcodes registered yet.
- Displays the item name, plus its description and the number of tags (labels) when present.
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
- Item description (shown only if set)
- **Barcodes** section — all barcodes that use this item type, each tappable to open its Barcode Detail

**Pull to refresh** — pull down to reload the item's linked barcodes.

**Fullscreen** — tap the ⛶ icon in the header to hide it and the bottom navigation so the details span the whole screen; it uses the same single, app-wide fullscreen toggle as the list tabs (see *Fullscreen mode* under *Search header*).

**Actions (authenticated):**
- **Edit** (pencil icon) — opens the Edit Item screen. Editable fields: Item (name), Item description, and (when backend supports images) Photo. The current item image is shown; tap the camera icon to take a new photo, or tap the trashcan to delete the current photo (tap undo to cancel). **Cancel** and **Save** buttons are at the bottom of the screen. The item list is refreshed after saving.
- **Delete** (trashcan icon) — opens a confirmation dialog and permanently deletes the item.
  - The trashcan is disabled (greyed out) if any barcodes are linked to the item.

---

### Persons tab

Searchable, paginated list of persons (requires login).

- Text search by name/nickname (shared search header — see *Search header* above; no filters, so no Filters toggle).
- Displays full name (primary), nickname (if different), and email per person.
- **Add new person** — a person-add **floating action button** (bottom-right) opens the New Person screen.
- Infinite scroll.
- **Pull to refresh** — pull down to force a reload.
- **Cached on tab switch** — the result set, active search string and scroll position are preserved when switching away and back.
- Tap any person to open its **Person Detail** page.
- **Arrow buttons and swipe left/right** navigate through the result set (same as Items and Barcodes).

---

### Person Detail

Reachable by tapping a person in the Persons tab.

**Displayed fields:** Nickname, First name, Last name, Email (each shown only when set; nickname is always shown). Long-press a field to copy its value (see *Barcode detail* for this shared behaviour).

- **Pull to refresh** — pull down to reload the person from the backend.
- **Fullscreen** — tap the ⛶ icon in the header to hide it and the bottom navigation; uses the same app-wide fullscreen toggle as the list tabs (see *Fullscreen mode* under *Search header*).
- **Arrow buttons and swipe left/right** navigate through the result set (same as Items and Barcodes).
- The footer keeps the **Persons** tab highlighted, and tapping the Persons icon from another tab returns to this Person Detail page (same as the other tabs' detail pages).

**Actions:**
- **Edit** (pencil icon) — opens the Edit Person screen. Editable fields: Nickname (required), First name, Last name, Email. **Cancel** and **Save** buttons are at the bottom of the screen.
- **Delete** (trashcan icon) — opens a confirmation dialog and permanently deletes the person.

---

### New Person

Reachable via the person-add floating action button in the Persons tab. Same form as Edit Person: Nickname (required), First name, Last name, Email, with **Cancel** and **Save** at the bottom. On success, the new person's **Person Detail** page opens.

---

### Verify workflow

Used to audit whether the physical contents of a container match the database. The verify happens
**on the container's Barcode Detail page** in verify mode — there is no separate verify screen.

1. **Home → Verify** opens the location scanner.
2. Scan the container/location barcode. The scanner switches to content mode and the app loads that
   barcode's detail in the background.
3. Scan each item found inside the container. Recognised scans flash the scanner border green; see
   *Barcode Scan Sound Conventions* for the audio feedback.
4. Tap **Done** to land on the container's **Barcode Detail** page with verify mode on, or **Cancel**
   (or the back arrow) to abort and return to the Home screen.

On the Barcode Detail page the *Content* section shows the two-column **Current | Scanned** table
(see *Content list* under *Barcode Detail* for the full description of the columns, the verify/adjust
icons, and the keep/remove adjust mode). Tapping a barcode entry navigates to its detail; the back
button returns.

The bottom buttons depend on whether the scanned content matches the database:

**If there are mismatches** (or any adjust-mode overrides):
- **Cancel** — discards the scan and leaves verify mode.
- **Save** — writes the scanned reality to the backend (updates parent references), honouring any
  per-row overrides set in adjust mode. Children that are currently out on loan are kept attached even
  if they were not physically scanned.

**If everything matches:**
- **Verify next** — leaves this container and reopens the location scanner to verify the next one.
- **OK** — leaves verify mode and stays on the container's detail page.

These buttons appear for **any** active verify once the content matches — whether the verify was
started from **Home → Verify** or from the Barcode Detail verify icon.

---

### New Barcode

Form to register a new barcode in the system.

| Field | Notes |
|---|---|
| **Item** * | Type-ahead search against the item catalogue (min. 2 characters, 300 ms debounce). Selecting a suggestion fills in the item description; typing an existing item's exact name selects it automatically (no tap needed). If the name does not exist yet, a new item is created on save. |
| **Barcode** * | The barcode string. Enter manually, scan (camera icon — an already-existing code is rejected with a rising tone), or tap **+1** to auto-generate the next available numeric code. While the +1 search is running a spinner is shown and the field is read-only; it becomes editable again once a free code is found. |
| **Location** | Optional parent barcode. Type-ahead barcode search (min. 2 characters, 300 ms debounce) — suggestions show item name and code. Or tap the scan icon to scan a barcode. |
| **Description** | Free-text description for this specific barcode instance. |
| **Item description** | Pre-filled from the selected item (read-only) — including when an existing item is selected by typing its exact name. Editable when no item is selected (and a new item is created on save). |
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

Shows a loan's full details as labelled field rows (long-press a row to copy its value), with previous/next navigation pinned to the bottom of the screen:
- Status (coloured red for TAKEN, green for RETURNED), person, description, taken/return/returned dates — each as a labelled row. *Return by* turns red when overdue.
- Full list of barcodes (labelled *Barcodes (N)*) formatted as *item name (code)* with the code in grey, like the Barcode Detail contents — each is a tappable link to its Barcode Detail screen (long-press to copy the code). Resolved item names are cached, so revisiting a loan or swiping between loans shows them instantly without re-fetching.
- **Arrow buttons** (and swipe left/right) to navigate to the previous/next loan in the list, pinned to the bottom of the screen.
- **Fullscreen** — tap the ⛶ icon in the header to hide it and the bottom navigation; uses the same app-wide fullscreen toggle as the list tabs (see *Fullscreen mode* under *Search header*).
- **Return loan** button — visible when status is TAKEN and the loan belongs to the current user (regardless of whether it was opened from Loans or My Loans). Opens a confirmation dialog; on confirm, marks the loan as returned and refreshes both loan lists.
- The footer keeps the tab the loan was opened from highlighted — **My Loans** when opened from My Loans, otherwise **Loans**.
- A loan detail can be open on the Loans tab and the My Loans tab at the same time; tapping the footer icons switches directly between the two open loan details (and to a tab's list when it has no loan open).

### Loan Cart

When one or more barcodes have been added to the loan selection (via the shopping cart icon in Barcode Detail or via the checkbox in the Barcodes tab), a **🛒 N** badge appears in the top bar on all non-camera screens. Tapping it opens the Loan Cart screen.

**Creating a loan:**
1. Add barcodes to the cart using the cart icon in Barcode Detail (long-press it for a single-item express loan that opens this screen directly).
2. Tap the cart badge in the top bar.
3. Review the barcode list (each entry is a tappable link to its Barcode Detail). Remove items with ×.
4. Optionally enter a **Description** for the loan.
5. Optionally set a **Return date** via the calendar picker or by typing `YYYY-MM-DD`.
6. **Preview** — shows which barcodes are available (green) and which are blocked (red, already on loan). Each entry is a tappable link to its Barcode Detail.
7. **Confirm** — creates the loan with the available barcodes. On success, the cart is cleared and you are navigated to the My Loans tab.

---

## Camera / scan screens

Every camera screen (Details, Verify, content scan, scan-to-search, location/parent scan, and the New Barcode code/parent scanners) shares the same scanner view: a back arrow top-left and, on devices whose camera has a flash unit, a **flashlight toggle** top-right. The flashlight is off each time a scanner opens and is switched off automatically when you leave the screen.

## Barcode Scan Sound Conventions

Every decoded barcode plays a short **acknowledge beep** immediately. In the content/verify scanners, that beep is then followed by extra beeps that encode the result:

| Sound | Meaning |
|---|---|
| Acknowledge beep | A barcode was decoded (plays immediately on every scan) |
| Acknowledge beep, then **1** beep | Content/verify: item is already expected in this container (matches the database) |
| Acknowledge beep, then **2** beeps | Content/verify: duplicate — already scanned in this session |
| Burp (NACK) | Unknown barcode / not found (plays after the acknowledge beep) |
| Rising tone | Invalid action (e.g. scanning the container's own barcode as its content) |

A plain acknowledge beep with **no** follow-up means the scan succeeded with nothing more to flag (a known barcode in the simple scanners, or a new/unexpected item in a content/verify scan).

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

The top bar also holds a **Logout** button when logged in (or a **Login** button in read-only mode — see *Read-only Mode*). **Logout** ends the session, clears the loan selection, and returns to the login screen.

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
