# lgr-android — Claude instructions

## Commits

Do not add a `Co-Authored-By:` line to commit messages.

## Source file headers

Start every new source file (Kotlin `.kt` and Gradle `.kts` build scripts) with the SPDX header,
before the `package`/imports:

```
// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0
```

## README maintenance

After implementing any feature, fix, or behaviour change, check whether `README.md` needs updating and apply the update in the same session without waiting to be asked. Only update if the change is user-visible or changes documented behaviour; internal refactors that leave behaviour unchanged do not need a README entry.
