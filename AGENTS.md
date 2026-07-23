# Agent instructions

Before writing or modifying any extension, multisrc theme, or lib code in this repository, **read
[CONTRIBUTING.md](CONTRIBUTING.md) in full**. It is the source of truth for conventions here and is
updated frequently - do not rely on prior knowledge of this codebase or of Tachiyomi/Mihon
extensions in general, since common patterns have changed.

A few points that are easy to get wrong from stale training data:

- **New sources must extend `KeiSource`** (`libVersion = "1.6"`), never `HttpSource` directly.
  `HttpSource`/`libVersion = "1.4"` only exists in extensions/themes not yet migrated - see
  [KeiSource](CONTRIBUTING.md#keisource).
  - **Metadata is injected via KSP:** Do NOT manually declare or `override val name`, `lang`, `id`, or `baseUrl` in your `@Source` class. These are defined using `source {}` blocks in `build.gradle.kts` and injected automatically.
- **`SourceFactory` is obsolete:** Do NOT implement `SourceFactory` to create multiple sources. Instead, add multiple `source {}` blocks in the extension's `build.gradle.kts`.
- **Generated Preferences:** Do NOT write manual `SharedPreferences` logic for base URL mirrors or custom user URLs. Use `baseUrl { mirrors(...) }` or `baseUrl { custom(...) }` inside the `source {}` block in `build.gradle.kts`.
- **Deeplinks are DSL-driven:** Declare URL intent filters using the `deeplink {}` block in `build.gradle.kts`. Do NOT modify `AndroidManifest.xml` or write manual intent filtering logic.
- **DTOs must be regular classes:** Do NOT use `data class` for `@Serializable` JSON/Protobuf DTOs (it bloats bytecode). Use a regular `class`, make fields `private` where possible, and only use `@SerialName` when the JSON key differs from the camelCase property name.
- **No local JSON/Proto instances:** Do NOT create `private val json: Json by injectLazy()`. Use the shared `keiyoushi.utils` helpers like `response.parseAs<T>()`, `toJsonRequestBody()`, or `parseAsProto<T>()`.
- Use `ext-bootstrap.py` to scaffold new extensions rather than hand-writing the module structure -
  see [Using ext-bootstrap.py](CONTRIBUTING.md#using-ext-bootstrappy).
- Prefer the helpers documented under
  [keiyoushi.utils (core utilities)](CONTRIBUTING.md#keiyoushiutils-core-utilities) (JSON parsing,
  HTTP requests, date parsing, GraphQL, WebView execution, etc.) over hand-rolled equivalents.
- Check [Available libs](CONTRIBUTING.md#available-libs) before implementing something from scratch
  that a `lib/` module may already solve.
- Don't add excessive or obvious comments explaining what code does; only comment on non-obvious
  *why* (a workaround, a subtle invariant, a site-specific quirk).
- Don't over-engineer. No speculative abstractions, config options, or generalization beyond what
  was asked, and no "just in case" error handling, fallbacks, or validation for scenarios that
  can't actually happen.

If anything in this file conflicts with `CONTRIBUTING.md`, `CONTRIBUTING.md` wins.

## Scope

For a typical "add/fix a source" task, only change files under `src/` (individual extensions),
`lib-multisrc/` (multisrc themes), or - in rare cases - `lib/`. Leave `core/`, `compiler/`,
`common/`, `gradle/`, and other build-logic/infrastructure files alone unless you were explicitly
asked to change them - that's a different, higher-risk category of work.

## Before committing / opening a PR

Format the module(s) you touched, not the whole repo - a full-repo Spotless run is slow on
this monorepo. Use the module's Gradle path, e.g. for a single extension:

```bash
./gradlew :src:en:mysource:spotlessApply
```

or for a theme: `./gradlew :lib-multisrc:madara:spotlessApply`.

Spotless also runs as part of `preBuild`, so a full build would catch violations too - running it
scoped like this first is just faster.

## Opening a pull request

If you are an AI agent asked to open a pull request, disclose that at the end of the PR
description: what you were asked to do, and that the PR was opened by an AI agent. Start that note
with a 🤖 emoji. This is in addition to, not a substitute for, the "This PR is AI-assisted..."
checklist item in the [Pull Request checklist](CONTRIBUTING.md#pull-request-checklist) - that box
still needs to be checked by the human who reviewed your changes.
