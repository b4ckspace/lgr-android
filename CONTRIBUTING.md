# Contributing to LGR Android

Thanks for your interest in improving the LGR Android app! This is a small project, so
the process is lightweight.

## Reporting bugs and requesting features

Please open a GitHub issue and include, where relevant:

- what you expected to happen vs. what actually happened (steps to reproduce);
- the app version (shown bottom-right on the login screen) and your device / Android version;
- the LGR backend version or any relevant server behaviour;
- screenshots or logs if they help.

## Building

See the **Building** section of the [README](README.md) for the toolchain (Android Studio /
JDK / SDK versions) and signing setup. A debug build needs no signing configuration; release
signing is optional and read from a local, untracked `keystore.properties`.

## Making changes

1. Fork the repository and create a branch off `master`.
2. Keep each change focused; prefer one logical change per commit.
3. Make sure the project builds (`./gradlew assembleDebug`) before opening a pull request.
4. If your change is user-visible or alters documented behaviour, **update `README.md`** in the
   same change.
5. New source files should carry the standard SPDX header:
   ```kotlin
   // SPDX-FileCopyrightText: <year> <your name>
   // SPDX-License-Identifier: Apache-2.0
   ```
6. Open a pull request against `master` describing what changed and why.

## Code style

- Kotlin with Jetpack Compose; follow the existing structure (MVVM with a single
  `AppViewModel`, screens under `ui/screens`, data layer under `data/`).
- Match the surrounding code's formatting, naming, and comment density rather than introducing
  new conventions.

## License

By contributing, you agree that your contributions are licensed under the project's
[Apache License 2.0](LICENSE).
