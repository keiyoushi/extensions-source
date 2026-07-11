# Contributing

This guide provides instructions and tips on creating a new Keiyoushi extension. Please **read
it carefully** if you are a new contributor or lack experience with the required languages
and knowledge.

This guide is not definitive and is updated over time. If you find any issues, feel
free to report them through a [Meta Issue](https://github.com/keiyoushi/extensions-source/issues/new?assignees=&labels=Meta+request&template=06_request_meta.yml)
or fix them directly by submitting a Pull Request.

## Table of Contents

- [Contributing](#contributing)
  - [Table of Contents](#table-of-contents)
  - [Prerequisites](#prerequisites)
    - [Tools](#tools)
    - [Cloning the repository](#cloning-the-repository)
  - [Getting help](#getting-help)
  - [Writing an extension](#writing-an-extension)
    - [Setting up a new Gradle module](#setting-up-a-new-gradle-module)
      - [Using ext-bootstrap.py](#using-ext-bootstrappy)
    - [Loading a subset of Gradle modules](#loading-a-subset-of-gradle-modules)
      - [Extension file structure](#extension-file-structure)
      - [build.gradle.kts](#buildgradlekts)
    - [Source declaration](#source-declaration)
      - [Annotate your source class](#annotate-your-source-class)
      - [Declare sources in build.gradle.kts](#declare-sources-in-buildgradlekts)
      - [baseUrl modes](#baseurl-modes)
      - [Multiple sources from one class](#multiple-sources-from-one-class)
    - [Core dependencies](#core-dependencies)
      - [Extension API](#extension-api)
      - [lib tools](#lib-tools)
      - [Available libs](#available-libs)
      - [Adding a lib dependency](#adding-a-lib-dependency)
      - [Creating a new lib](#creating-a-new-lib)
      - [keiyoushi.utils (core utilities)](#keiyoushiutils-core-utilities)
        - [JSON parsing - `parseAs`](#json-parsing---parseas)
        - [JSON serialization - `toJsonString` / `toJsonRequestBody`](#json-serialization---tojsonstring--tojsonrequestbody)
        - [JSON models (DTOs) and serialization](#json-models-dtos-and-serialization)
        - [Protobuf parsing and serialization - `parseAsProto` / `toRequestBodyProto`](#protobuf-parsing-and-serialization---parseasproto--torequestbodyproto)
        - [Date parsing - `tryParse`](#date-parsing---tryparse)
        - [Filter helpers - `firstInstance` / `firstInstanceOrNull`](#filter-helpers---firstinstance--firstinstanceornull)
        - [SharedPreferences - `getPreferences` / `getPreferencesLazy`](#sharedpreferences---getpreferences--getpreferenceslazy)
        - [Next.js data extraction - `extractNextJs` / `extractNextJsRsc`](#nextjs-data-extraction---extractnextjs--extractnextjsrsc)
        - [Extracting URLs - `setUrlWithoutDomain` + `absUrl`](#extracting-urls---seturlwithoutdomain--absurl)
        - [GraphQL Requests - `graphQLPost` / `parseGraphQLAs`](#graphql-requests---graphqlpost--parsegraphqlas)
        - [GraphQL GET requests - `graphQLGet`](#graphql-get-requests---graphqlget)
        - [JsonElement accessor helpers](#jsonelement-accessor-helpers)
        - [ZIP streaming - `readZipDirectory` / `readZipEntry`](#zip-streaming---readzipdirectory--readzipentry)
      - [Additional dependencies](#additional-dependencies)
    - [Extension main class](#extension-main-class)
      - [Main class key variables](#main-class-key-variables)
    - [HTML and Image Processing](#html-and-image-processing)
    - [OkHttp and Network](#okhttp-and-network)
    - [Extension call flow](#extension-call-flow)
      - [Popular Manga](#popular-manga)
      - [Latest Manga](#latest-manga)
      - [Manga Search](#manga-search)
        - [Filters](#filters)
      - [Manga Details](#manga-details)
      - [Chapter](#chapter)
      - [Chapter Pages](#chapter-pages)
    - [Misc notes](#misc-notes)
    - [Advanced Extension features](#advanced-extension-features)
      - [Extension logic and app features](#extension-logic-and-app-features)
      - [Configurable Sources and Preferences](#configurable-sources-and-preferences)
      - [URL intent filter](#url-intent-filter)
      - [Update strategy](#update-strategy)
      - [Renaming existing sources](#renaming-existing-sources)
  - [Multi-source themes](#multi-source-themes)
    - [Creating a new theme](#creating-a-new-theme)
      - [Theme directory structure](#theme-directory-structure)
      - [Theme build.gradle.kts](#theme-buildgradlekts)
      - [Theme main class](#theme-main-class)
    - [Using a Theme](#using-a-theme)
  - [Running](#running)
  - [Debugging](#debugging)
    - [Android Debugger](#android-debugger)
    - [Logs](#logs)
    - [Inspecting network calls](#inspecting-network-calls)
    - [Using external network inspecting tools](#using-external-network-inspecting-tools)
      - [Set up your proxy server](#set-up-your-proxy-server)
      - [OkHttp proxy setup](#okhttp-proxy-setup)
  - [Building](#building)
  - [Submitting the changes](#submitting-the-changes)
    - [Pull Request checklist](#pull-request-checklist)

## Prerequisites

Before you start, please note that the ability to use following technologies is **required** and
that existing contributors will not actively teach these to you.

- Basic [Android development](https://developer.android.com/)
- [Kotlin](https://kotlinlang.org/)
- Web scraping
  - [HTML](https://developer.mozilla.org/en-US/docs/Web/HTML)
  - [CSS selectors](https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_Selectors)
  - [OkHttp](https://square.github.io/okhttp/)
  - [JSoup](https://jsoup.org/)

### Tools

- [Android Studio](https://developer.android.com/studio)
- Emulator or phone with developer options enabled and a recent version of Mihon installed
- [Icon Generator](https://as280093.github.io/AndroidAssetStudio/icons-launcher.html)
- [Try jsoup](https://try.jsoup.org/)

### Cloning the repository

Some alternative steps can be followed to skip unrelated sources, which will make it faster to pull,
navigate and build. This will also reduce disk usage and network traffic.

**Due to the large size of this repository, it is highly recommended to do a partial clone to save network traffic and disk space.**

<details><summary>Steps</summary>

1. Do a partial clone.

   ```bash
   git clone --filter=blob:none --sparse <fork-repo-url>
   cd extensions-source/
   ```

2. Configure sparse checkout.

   There are two modes of pattern matching. The default is cone mode.
   Cone mode enables significantly faster pattern matching for big monorepos
   and the sparse index feature to make Git commands more responsive.
   In this mode, you can only filter by file path, which is less flexible
   and might require more work when the project structure changes.

   Cone mode is the recommended standard for pattern matching and responsiveness. Using non-cone mode is deprecated and discouraged.

   ```bash
   git sparse-checkout set --cone --sparse-index
   # add project folders
   git sparse-checkout add common compiler core gradle lib lib-multisrc
   # add a single source
   git sparse-checkout add src/<lang>/<source>
   ```

   To remove a source, open `.git/info/sparse-checkout` and delete the exact
   lines you typed when adding it. Don't touch the other auto-generated lines
   unless you fully understand how cone mode works, or you might break it.

   ### Non-cone mode (Deprecated/Discouraged)

   Using non-cone mode is deprecated and not recommended. If you still need it, follow these steps:

   ```bash
   # enable sparse checkout
   git sparse-checkout set --no-cone
   # edit sparse checkout filter
   vim .git/info/sparse-checkout
   # alternatively, if you have VS Code installed
   code .git/info/sparse-checkout
   ```

   Here's an example:

   ```bash
   /*
   !/src/*
   !/multisrc-lib/*
   # allow a single source
   /src/<lang>/<source>
   # allow a multisrc theme
   /lib-multisrc/<source>
   # or type the source name directly
   <source>
   ```

   Explanation: the rules are like `gitignore`. We first exclude all sources
   while retaining project folders, then add the needed sources back manually.

3. Configure remotes.

   ```bash
   # add upstream
   git remote add upstream <keiyoushi-repo-url>
   # optionally disable push to upstream
   git remote set-url --push upstream no_pushing
   # optionally fetch main only (ignore all other branches)
   git config remote.upstream.fetch "+refs/heads/main:refs/remotes/upstream/main"
   # update remotes
   git remote update
   # track main of upstream instead of fork
   git branch main -u upstream/main
   ```

4. Useful configurations. (optional)

   ```bash
   # prune obsolete remote branches on fetch
   git config remote.origin.prune true
   # fast-forward only when pulling main branch
   git config pull.ff only
   # Add an alias to sync main branch without fetching useless blobs.
   # If you run `git pull` to fast-forward in a blobless clone like this,
   # all blobs (files) in the new commits are still fetched regardless of
   # sparse rules, which makes the local repo accumulate unused files.
   # Use `git sync-main` to avoid this. Be careful if you have changes
   # on main branch, which is bad practice.
   git config alias.sync-main '!git switch main && git fetch upstream && git reset --keep FETCH_HEAD'
   ```

5. Later, if you change the sparse checkout filter, run `git sparse-checkout reapply`.

Read more on
[Git's object model](https://github.blog/2020-12-17-commits-are-snapshots-not-diffs/),
[partial clone](https://github.blog/2020-12-21-get-up-to-speed-with-partial-clone-and-shallow-clone/),
[sparse checkout](https://github.blog/2020-01-17-bring-your-monorepo-down-to-size-with-sparse-checkout/),
[sparse index](https://github.blog/2021-11-10-make-your-monorepo-feel-small-with-gits-sparse-index/),
and [negative refspecs](https://github.blog/2020-10-19-git-2-29-released/#user-content-negative-refspecs).

</details>

## Getting help

- Join [the Discord server](https://discord.gg/3FbCpdKbdY) for online help and to ask questions during
  development. Please ask questions in the `#programming` channel.
- There are features and tricks not explored in this document; refer to existing
  extension code for examples.

## Writing an extension

The quickest way to get started is by using the [ext-bootstrap.py](#using-ext-bootstrappy) script.
We also recommend reading through the code of a few existing extensions before beginning.

### Setting up a new Gradle module

Each extension should reside in `src/<lang>/<mysourcename>`. Use `all` as `<lang>` if your target
source supports multiple languages or if it could support multiple sources.

The `<lang>` used in the folder inside `src` should be the major `language` part. For example, if
you will be creating a `pt-BR` source, use `<lang>` here as `pt` only. Inside the source class, use
the full locale string instead.

#### Using ext-bootstrap.py

Instead of setting this up by hand, you can use the `ext-bootstrap.py` script to scaffold a new
extension module automatically:

```console
$ python ext-bootstrap.py -n "My Source" -l en -u https://mysource.com
```

This creates `src/<lang>/<mysourcename>/build.gradle.kts` along with the extension's package
directory and a starter source class implementing `HttpSource`.

Available options:

| Flag                         | Description                                                              |
|------------------------------|--------------------------------------------------------------------------|
| `-n`, `--extname`            | Extension name                                                           |
| `-l`, `--lang`, `--language` | Extension language (2- or 3-letter ISO code, or `all`)                   |
| `-u`, `--baseurl`            | Extension base URL (must be `https://`)                                  |
| `--source-name`              | Source name (defaults to `--extname`)                                    |
| `-c`, `--content-warning`    | `SAFE`, `MIXED`, or `NSFW` (default: `SAFE`)                             |
| `-m`, `--multisrc`           | Name of an existing multisrc theme to base the source on                 |
| `--path`                     | Path to the extension repo directory (defaults to the current directory) |

For example, to scaffold a source based on the `madara` multisrc theme:

```console
$ python ext-bootstrap.py -n "My Source" -l en -u https://mysource.com -m madara
```

### Loading a subset of Gradle modules

By default, all individual and multisrc extensions are loaded for local development.
This may be inconvenient and can drastically slow down your system when working on a single extension.

To adjust which modules are loaded, make adjustments to the `settings.gradle.kts` file as needed. You can specify the single extension you want to work on in the `load individual extension` function. This helps avoid loading unnecessary modules, making the build process more efficient and preventing your CPU from being overworked.

#### Extension file structure

The simplest extension structure looks like this:

```console
$ tree src/<lang>/<mysourcename>/
src/<lang>/<mysourcename>/
├── build.gradle.kts
├── res
│   ├── mipmap-hdpi
│   │   └── ic_launcher.png
│   ├── mipmap-mdpi
│   │   └── ic_launcher.png
│   ├── mipmap-xhdpi
│   │   └── ic_launcher.png
│   ├── mipmap-xxhdpi
│   │   └── ic_launcher.png
│   └── mipmap-xxxhdpi
│       └── ic_launcher.png
└── src
    └── eu
        └── kanade
            └── tachiyomi
                └── extension
                    └── <lang>
                        └── <mysourcename>
                            ├── <MySourceName>.kt
                            ├── <Dto>.kt (optional)
                            └── <Filters>.kt (optional)

```

`<lang>` should be an ISO 639-1 compliant language code (two letters or `all`). `<mysourcename>`
should be adapted from the site name, and can only contain lowercase ASCII letters and digits.
Your extension code must be placed in the package `eu.kanade.tachiyomi.extension.<lang>.<mysourcename>`.

> [!TIP]
> Additional files in the extension package (like `Dto.kt`, `Filters.kt`)
> should NOT repeat the extension name (e.g. use `Dto.kt` instead of `MySourceNameDto.kt`).
> Note: While older extensions might use the repeated name pattern, avoiding it is a newly enforced convention to maintain consistency across the repository.

#### build.gradle.kts

Extensions' `build.gradle.kts` should look like this:

```kotlin
import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Example" // Replace with your actual source name
    versionCode = 1
    contentWarning = ContentWarning.NSFW // Options: ContentWarning.SAFE, ContentWarning.MIXED, ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "Example" // Optional, defaults to the top-level name
        lang = "en"
        baseUrl = "https://example.com"
    }
}
```

At least one `source {}` block is required for every extension.

| Field            | Description                                                                                                                                                                                                                                                                                                              |
|------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `name`           | The name of the extension. Should be romanized if site name is not in English.                                                                                                                                                                                                                                           |
| `versionCode`    | The extension version code. This must be a positive integer and incremented with any change to the code. Do not bump for changes that do not affect users, such as changing a private function to a public function.                                                                                                     |
| `contentWarning` | Content safety classification. Must be set explicitly to one of `ContentWarning.SAFE`, `ContentWarning.MIXED`, or `ContentWarning.NSFW`.                                                                                                                                                                                 |
| `libVersion`     | The extension library version. Always set to `"1.4"`.                                                                                                                                                                                                                                                                    |
| `theme`          | Name of a multi-source theme from `lib-multisrc/` to inherit from (e.g. `"madara"`). When set, the extension's version code is `theme.baseVersionCode + versionCode`.                                                                                                                                                    |
| `source {}`      | Declares one source (or multiple, for multi-language or multi-mirror extensions) using KSP code generation. This block is mandatory. See [Source declaration](#source-declaration).                                                                                                                                      |
| `deeplink {}`    | Declares a URL deeplink intent filter. See [URL intent filter](#url-intent-filter).                                                                                                                                                                                                                                      |

The extension's version name is generated automatically by concatenating `libVersion` and the calculated version code.
With the example used above, the version would be `1.4.1`.

### Source declaration

Sources are registered through `source {}` blocks in `build.gradle.kts`, combined with the `@Source` annotation on your source class. The build system uses KSP to generate a subclass (`ExtensionGenerated`) that automatically injects `name`, `lang`, `id`, and `baseUrl`- you no longer need to declare them manually in Kotlin.

#### Annotate your source class

Add `@Source` to your main class and remove any manual declarations of `name`, `lang`, `id`, and `baseUrl`:

```kotlin
import keiyoushi.annotation.Source

@Source
abstract class MySource : HttpSource() {
    // name, lang, id, and baseUrl are injected automatically - do not declare them here.
    // All other overrides go here as normal.
}
```

#### Declare sources in build.gradle.kts

Add one or more `source {}` blocks inside `keiyoushi {}`:

```kotlin
keiyoushi {
    name = "My Source"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://example.com"
    }
}
```

| Field         | Description                                                                                                                                                                                                                                                                                                                                              |
|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `name`        | The source name shown in the app. Optional; defaults to the top-level extension `name`.                                                                                                                                                                                                                                                                  |
| `lang`        | ISO 639-1 language code. Required.                                                                                                                                                                                                                                                                                                                       |
| `baseUrl`     | The source's base URL. See [baseUrl modes](#baseurl-modes) below.                                                                                                                                                                                                                                                                                        |
| `id`          | Explicit source ID. Optional; auto-computed from `name + lang + versionId` if omitted. Set this explicitly when renaming a source to preserve users' libraries.                                                                                                                                                                                          |
| `versionId`   | Integer used as a seed for auto-computing `id`. Defaults to `1`. Only bump this if the source's URL structure fundamentally changes and old entries can no longer be redirected.                                                                                                                                                                         |

A source class may compute `name` and/or `baseUrl` itself by declaring `override val name` / `override val baseUrl`; codegen detects the override and skips generating that property (the DSL value is then used only for metadata such as the repo index and deeplink hosts). **This is discouraged** — prefer letting the DSL own `name` and `baseUrl`, and only override them in the source class when you have a very specific reason. `baseUrl` may only be overridden when the DSL declares a plain static `baseUrl` — the `mirrors`/`custom` modes generate preference infrastructure and cannot be hand-overridden. `id` and `lang` are always owned by the DSL; overriding them in the source class is an error.

#### baseUrl modes

The `baseUrl` field inside `source {}` supports three modes:

**Static** (single URL, no preferences UI):

```kotlin
source {
    lang = "en"
    baseUrl = "https://example.com"
}
```

**Mirrors** (user picks a mirror from a list - preference UI is generated automatically):

```kotlin
source {
    lang = "en"
    baseUrl {
        mirrors(
            "https://example.com",
            "https://mirror1.com",
            "https://mirror2.com",
        )
    }
}
```

The first url is the default; each mirror's host is shown in the picker. To show custom labels instead, pass `"label" to "url"` pairs — either all urls are naked or all are labeled, not a mix:

```kotlin
baseUrl {
    mirrors(
        "Main" to "https://example.com",
        "Mirror" to "https://mirror1.com",
    )
}
```

The extension automatically implements `ConfigurableSource` and adds a "Preferred mirror" `ListPreference` to the settings screen. You do not need to write any `SharedPreferences` code or add `setupPreferenceScreen`. If your class already implements `ConfigurableSource`, `super.setupPreferenceScreen(screen)` is called so your own preferences are preserved.

**Custom URL** (user can enter any URL - preference UI with validation is generated automatically):

```kotlin
source {
    lang = "en"
    baseUrl {
        custom("https://example.com")
    }
}
```

Like `mirrors`, the extension automatically implements `ConfigurableSource` and adds a validated "Custom base URL" `EditTextPreference`. The default URL is restored automatically if the hardcoded default changes in a future update.

> [!IMPORTANT]
> When using `mirrors(...)` or `custom(...)`, **do not** implement mirror/URL selection manually in your class using `SharedPreferences` or a `ListPreference` - the generated code handles it. Doing both will create duplicate preferences.

#### Multiple sources from one class

To expose multiple sources (previously done with `SourceFactory`), add multiple `source {}` blocks. Each block generates an anonymous inner class that subclasses your `@Source` class:

```kotlin
keiyoushi {
    name = "Example"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "Example EN"
        lang = "en"
        baseUrl = "https://en.example.com"
    }

    source {
        name = "Example JP"
        lang = "ja"
        baseUrl = "https://jp.example.com"
    }
}
```

The generated `ExtensionGenerated` class implements `SourceFactory` automatically. You do not need to implement `SourceFactory` yourself.

### Core dependencies

#### Extension API

Extensions rely on [extensions-lib](https://github.com/tachiyomiorg/extensions-lib), which provides
some interfaces and stubs from the [app](https://github.com/mihonapp/mihon) for compilation
purposes. The actual implementations can be found [in the Mihon source code](https://github.com/mihonapp/mihon/tree/main/app/src/main/java/eu/kanade/tachiyomi/source).
Referencing the actual implementation will help with understanding extensions' call flow.

#### lib tools

The `lib/` directory contains reusable Gradle modules that solve common problems shared across
multiple extensions, such as cookie injection, image descrambling, JavaScript deobfuscation, and
more. Before implementing something from scratch, check whether an existing lib already covers your
use case. Each lib is self-documented via KDoc comments and/or a README in its own folder.

#### Available libs

| Module                                                                                                    | Description                                                                             |
|-----------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| [`lib-cookieinterceptor`](https://github.com/keiyoushi/extensions-source/tree/main/lib/cookieinterceptor) | Injects cookies into OkHttp requests for a given domain                                 |
| [`lib-cryptoaes`](https://github.com/keiyoushi/extensions-source/tree/main/lib/cryptoaes)                 | AES-CBC decryption compatible with CryptoJS; JSFuck deobfuscation                       |
| [`lib-dataimage`](https://github.com/keiyoushi/extensions-source/tree/main/lib/dataimage)                 | Decodes base64 `data:image` strings into mock URLs that OkHttp can handle               |
| [`lib-e4p`](https://github.com/keiyoushi/extensions-source/tree/main/lib/e4p)                             | Decodes and decrypts E4P-format manga page archives (TIFF/XEBP)                         |
| [`lib-i18n`](https://github.com/keiyoushi/extensions-source/tree/main/lib/i18n)                           | Internationalization helper (`Intl`) for multi-language UI strings in extensions        |
| [`lib-lzstring`](https://github.com/keiyoushi/extensions-source/tree/main/lib/lzstring)                   | LZ-String decompression and compression                                                 |
| [`lib-publus`](https://github.com/keiyoushi/extensions-source/tree/main/lib/publus)                       | Handles Publus DRM-protected reader decryption, unscrambling, and page loading          |
| [`lib-randomua`](https://github.com/keiyoushi/extensions-source/tree/main/lib/randomua)                   | Fetches and rotates real-world User-Agent strings (requires overriding `getMangaUrl()`) |
| [`lib-secretstream`](https://github.com/keiyoushi/extensions-source/tree/main/lib/secretstream)           | ChaCha20/Poly1305/X25519 cryptography for secret-stream encrypted sources               |
| [`lib-seedrandom`](https://github.com/keiyoushi/extensions-source/tree/main/lib/seedrandom)               | Seeded deterministic pseudo-random number generation (ARC4-based)                       |
| [`lib-speedbinb`](https://github.com/keiyoushi/extensions-source/tree/main/lib/speedbinb)                 | Processes, decrypts, and descrambles SpeedBinb reader payloads                          |
| [`lib-synchrony`](https://github.com/keiyoushi/extensions-source/tree/main/lib/synchrony)                 | JavaScript deobfuscation via the Synchrony engine (QuickJS sandbox)                     |
| [`lib-textinterceptor`](https://github.com/keiyoushi/extensions-source/tree/main/lib/textinterceptor)     | Renders plain text or HTML as a PNG image page                                          |
| [`lib-unpacker`](https://github.com/keiyoushi/extensions-source/tree/main/lib/unpacker)                   | Unpacks Dean Edwards-packed JavaScript; substring extraction helpers                    |
| [`lib-zipinterceptor`](https://github.com/keiyoushi/extensions-source/tree/main/lib/zipinterceptor)       | Decodes, stitches, and processes multi-page ZIP/AVIF/SVG image archives                 |

> [!IMPORTANT]
> If your module uses `:lib:randomua`, the Spotless check requires your extension to override the `getMangaUrl()` method in your main class, or the build will fail.

> [!NOTE]
> The table above highlights the most commonly used libraries. Check the `lib/` directory for the full list of available modules and their specific READMEs.

#### Adding a lib dependency

Declare the module in your extension's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":lib:<name>"))
}
```

> [!TIP]
> For multi-source themes in `lib-multisrc/`, use `api()` instead of `implementation()` so the dependency is transitively available to all extensions using the theme.

For example:

```kotlin
dependencies {
    implementation(project(":lib:dataimage"))
}
```

Gradle resolves transitive dependencies automatically, so you only need to declare the lib you are
directly using.

#### Creating a new lib

If no existing lib fits your needs and the functionality is generic enough to be shared across
multiple extensions, you can create a new one.

A lib follows this structure:

```console
lib/<mylibname>/
├── build.gradle.kts
└── src
    └── keiyoushi
        └── lib
            └── <mylibname>
                └── MyLib.kt
```

The `build.gradle.kts` must apply the `kei.plugins.library` plugin:

```kotlin
plugins {
    alias(kei.plugins.library)
}
```

If your lib depends on another lib, declare it in the same file:

```kotlin
plugins {
    alias(kei.plugins.library)
}

dependencies {
    implementation(project(":lib:other-lib")) // Replace with the actual other library name
}
```

Place your code in the package `keiyoushi.lib.<mylibname>`. Document public API with KDoc so
contributors can understand the lib without needing to read `CONTRIBUTING.md`.

#### keiyoushi.utils (core utilities)

The `core/utils` module provides a set of shared extension functions that are available to all extensions
without any extra Gradle dependency. Prefer using these helpers instead of implementing your own equivalents, as they provide standardized and maintained solutions.
The utilities live in the `keiyoushi.utils` package and are imported individually.

##### JSON parsing - `parseAs`

Use `keiyoushi.utils.parseAs` to deserialize JSON. It works on `String`, `Response`, `InputStream`, and `JsonElement` receivers and uses the shared `jsonInstance` (a pre-configured `Json` with `ignoreUnknownKeys = true`). The `Response` and `InputStream` variants use efficient stream decoding and automatically close the stream after reading.

```kotlin
import keiyoushi.utils.parseAs

// From a Response (uses streaming and consumes the body):
val dto = response.parseAs<MyDto>()

// From a String:
val dto = jsonString.parseAs<MyDto>()

// With a transform applied before parsing (e.g., stripping JSONP callbacks):
val dto = response.parseAs<MyDto> { it.substringAfter("callback(").dropLast(1) }
```

**Do not** create a local `private val json: Json by injectLazy()` unless you specifically need a custom JSON configuration (e.g., `isLenient = true` or custom serializers). For standard parsing, the global instance is already available via `jsonInstance` and the `parseAs` helpers use it automatically.

##### JSON serialization - `toJsonString` / `toJsonRequestBody`

Use `keiyoushi.utils.toJsonString` to serialize an object to a JSON string. If you are sending a POST/PUT request, use `keiyoushi.utils.toJsonRequestBody` to directly convert your object into an OkHttp `RequestBody` with the correct `application/json` media type.

```kotlin
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString

// To a RequestBody for OkHttp (recommended for APIs):
val body = myRequestDto.toJsonRequestBody()

// To a simple String:
val jsonString = myRequestDto.toJsonString()
```

##### JSON models (DTOs) and serialization

When defining `@Serializable` classes for JSON parsing, **do not** use `data class` unless you actually need data class features (like `copy()` or destructuring). Use a regular `class` instead to reduce the generated bytecode size.

Always use camelCase for Kotlin properties. Only use `@SerialName` when the JSON key does not match the property name (e.g., mapping a snake_case JSON key like `cover_img` to `coverImg`, or an invalid Kotlin identifier like `_count` to `count`). If the JSON key already matches the property name exactly, `@SerialName` is redundant and should be omitted. It is also recommended to make fields `private` if they are only used internally (for instance, when mapping directly to `SManga` or `SChapter` within the DTO).

```kotlin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Bad: Using data class and snake_case variable names
@Serializable
data class MyDto(val manga_id: Int, val cover_img: String)

// Good: Regular class, camelCase variables mapped with @SerialName only when names differ, and private fields
@Serializable
class MyDto(
    @SerialName("manga_id") private val mangaId: Int,
    @SerialName("cover_img") private val coverImg: String,
    private val title: String, // No @SerialName needed if JSON key is "title"
    @SerialName("_count") private val count: Int, // Needed for invalid Kotlin identifiers
) {
    fun toSManga() = SManga.create().apply {
        url = mangaId.toString()
        thumbnail_url = coverImg
        this.title = title
    }
}
```

- **Use `@Serializable` classes instead of `JsonObject`:** Do not manually traverse `JsonObject` or `JsonArray`. Define `@Serializable` classes and use `parseAs<T>()`.
- **Map only used fields:** Do not map all fields from the JSON response in your DTOs if they are not used. Omit unused fields to keep the class clean and reduce bytecode.
- **Mandatory fields should not have defaults:** Do not provide default empty/null values to mandatory fields (like a manga's ID or title) in DTOs just to avoid parsing exceptions. Let the parser fail early so broken entries are detected.
- **Avoid `buildJsonObject` for requests:** Instead of manually building `JsonObject` with `buildJsonObject { put(...) }`, define a `@Serializable` request DTO class and use `toJsonRequestBody()`.
- **Avoid manual JSON string reading:** Avoid manually reading the response body as a string to parse JSON (e.g., `response.body.string()` or `response.peekBody(Long.MAX_VALUE).string()` outside of interceptors). Use `response.parseAs<T>()` directly, which handles efficient stream decoding and automatically closes the response body.

##### Protobuf parsing and serialization - `parseAsProto` / `toRequestBodyProto`

If a source's API uses Protocol Buffers (Protobuf) instead of JSON, use the `keiyoushi.utils` helpers to decode and encode the data. These extensions use a shared `protoInstance` and automatically handle resource management.

```kotlin
import keiyoushi.utils.parseAsProto
import keiyoushi.utils.toRequestBodyProto
import keiyoushi.utils.decodeProtoBase64

// From a Response (automatically closes the body):
val dto = response.parseAsProto<MyProtoDto>()

// From a Response with a transform applied before decoding:
val dto = response.parseAsProto<MyProtoDto> { bytes -> bytes.drop(4).toByteArray() }

// Decoding a Base64-encoded Protobuf string:
val dto = base64String.decodeProtoBase64<MyProtoDto>()

// Creating a RequestBody for a POST request (defaults to application/protobuf):
val requestBody = myRequestDto.toRequestBodyProto()
```

If you only need to work with raw bytes, you can also use `.decodeProto()` and `.encodeProto()` directly on a `ByteArray`.

Do not create a local `private val proto: ProtoBuf by injectLazy()` unless you specifically need a custom configuration. For standard parsing, the global instance is already available and the `parseAsProto` helpers use it automatically.

##### Date parsing - `tryParse`

Use `keiyoushi.utils.tryParse` on a `SimpleDateFormat` instance to safely parse a date string.
It returns `0L` on failure or when the input is `null`, which is exactly what the app expects.

```kotlin
import keiyoushi.utils.tryParse

// Declare dateFormat at class/file level - creating SimpleDateFormat is expensive:
private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

chapter.date_upload = dateFormat.tryParse(dateStr)
```

**Do not** write manual try/catch blocks or null-guards around `SimpleDateFormat.parse()`;
`tryParse` handles both. Also, always declare your `SimpleDateFormat` as a class-level or
file-level `val` so it is not reconstructed for every chapter.

Two common mistakes to avoid:

- **Always set `Locale.ROOT`**, unless the pattern contains locale-sensitive text (such as month names), in which case use the appropriate locale.
- **Set the timezone** if known, either if the site's region is known or because the pattern uses a literal `'Z'`.

  ```kotlin
  // Wrong: 'Z' is treated as a literal character, timezone defaults to device local time
  SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
  // Correct:
  SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
      timeZone = TimeZone.getTimeZone("UTC")
  }
  // Also correct (Z without quotes parses the timezone offset from the string):
  SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT)
  ```

##### Filter helpers - `firstInstance` / `firstInstanceOrNull`

Use these instead of `filterIsInstance<T>().first()` / `filterIsInstance<T>().firstOrNull()`.

```kotlin
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
```

##### SharedPreferences - `getPreferences` / `getPreferencesLazy`

Use these instead of accessing `Injekt` manually.

```kotlin
import keiyoushi.utils.getPreferences
import keiyoushi.utils.getPreferencesLazy

// Inside your HttpSource class:
private val preferences by getPreferencesLazy()
```

> [!NOTE]
> `getPreferences()` and `getPreferencesLazy()` are extension functions on `HttpSource`. If you need to access preferences from a context without a source receiver (e.g. inside a helper class), use the top-level `getPreferences(sourceId)` function instead.

##### Next.js data extraction - `extractNextJs` / `extractNextJsRsc`

If the site is built with Next.js, use `keiyoushi.utils.extractNextJs` on a `Document` or `Response`,
or `keiyoushi.utils.extractNextJsRsc` on a raw RSC response string to pull typed data out of the
hydration payload without fragile HTML scraping.

```kotlin
import keiyoushi.utils.extractNextJs

val data = response.extractNextJs<MyDto>()
// Or with an explicit predicate:
val data = document.extractNextJs<MyDto> { element ->
    element is JsonObject && "slug" in element
}
```

For client-side navigation responses (`text/x-component` content type), pass the `rsc: 1`
request header. You can call `response.extractNextJs<T>()` directly on the `Response` object;
the utility automatically inspects the `Content-Type` header and safely routes to `extractNextJsRsc`
for you without needing to manually extract the response body string.

##### Extracting URLs - `setUrlWithoutDomain` + `absUrl`

When extracting URLs from HTML, prefer `element.absUrl("href")` or `element.attr("abs:href")` over manually concatenating `baseUrl` + `path`. Combined with `setUrlWithoutDomain()`, this safely handles both absolute and relative links.

```kotlin
// Risky - setUrlWithoutDomain cannot resolve all relative URLs:
setUrlWithoutDomain(element.attr("href"))
// Safe:
setUrlWithoutDomain(element.absUrl("href"))
```

##### GraphQL Requests - `graphQLPost` / `parseGraphQLAs`

If a source uses a GraphQL API, use the dedicated `keiyoushi.utils` helpers to build requests and
parse responses. These utilities automatically serialize variables, encode payload structures, and
throw a `GraphQLException` if the response contains GraphQL errors.

```kotlin
import keiyoushi.utils.graphQLPost
import keiyoushi.utils.parseGraphQLAs

// Define your variables as a @Serializable class
val variables = MyVariablesDto(page = 1)

// Building the request:
val request = graphQLPost(
    url = "$baseUrl/graphql",
    headers = headers,
    operationName = "SearchManga",
    query = $$"""
    query SearchManga($page: Int!) {
      mangas(page: $page) {
        id
      }
    }
    """,
    variables = variables
)

// Parsing the response (automatically extracts the "data" object):
val data = response.parseGraphQLAs<MyResponseDto>()
```

##### GraphQL GET requests - `graphQLGet`

For sources that send GraphQL over HTTP GET instead of POST, use `graphQLGet` with the same signature as `graphQLPost`:

```kotlin
import keiyoushi.utils.graphQLGet

val request = graphQLGet(
    url = "$baseUrl/graphql",
    headers = headers,
    query = $$"""
    query SearchManga($page: Int!) {
      mangas(page: $page) {
        id
      }
    }
    """,
    variables = variables
)
```

For sources that use [Automatic Persisted Queries (APQ)](https://www.apollographql.com/docs/kotlin/advanced/persisted-queries/), pass the result of `persistedQueryExtension(sha256Hash)` as the `extensions` parameter and omit `query`. This works for both `graphQLPost` and `graphQLGet`.

```kotlin
import keiyoushi.utils.persistedQueryExtension

val request = graphQLPost(
    url = "$baseUrl/graphql",
    headers = headers,
    operationName = "SearchManga",
    variables = variables,
    extensions = persistedQueryExtension("abc123sha256...")
)
```

To automatically throw `GraphQLException` for every request on a client rather than parsing per-response, add `GraphQLErrorInterceptor` to the `OkHttpClient`:

```kotlin
import keiyoushi.utils.GraphQLErrorInterceptor

override val client = network.client.newBuilder()
    .addInterceptor(GraphQLErrorInterceptor())
    .build()
```

##### JsonElement accessor helpers

`keiyoushi.utils` provides concise read-only accessors for traversing raw `JsonElement` trees. Import individually as needed:

```kotlin
import keiyoushi.utils.array
import keiyoushi.utils.boolean
import keiyoushi.utils.get
import keiyoushi.utils.int
import keiyoushi.utils.long
import keiyoushi.utils.obj
import keiyoushi.utils.string

val root: JsonElement = response.parseAs()
val title = root["data"]["title"].string
val count = root["data"]["count"].int
val items = root["data"]["items"].array
val nested = root["data"]["meta"].obj
```

`element[key]` returns `JsonElement?` (null-safe). The terminal accessors (`.string`, `.int`, `.long`, `.boolean`) throw if the element is null. `JsonObject` also has `getStringOrNull`, `getIntOrNull`, `getLongOrNull`, and `getBooleanOrNull` variants for optional fields.

Prefer these over writing `element.jsonObject["key"]?.jsonPrimitive?.content` manually.

##### ZIP streaming - `readZipDirectory` / `readZipEntry`

For sources that serve manga pages as remote ZIP archives, the `keiyoushi.zip` package lets you read the central directory and individual entries using HTTP Range requests - no need to download the entire file. Import from `keiyoushi.zip`:

```kotlin
import keiyoushi.zip.readZipDirectory
import keiyoushi.zip.readZipEntry
import keiyoushi.zip.range

// 1. Fetch the ZIP central directory (two Range requests at most).
val directory = readZipDirectory(totalFileSizeInBytes) { byteRange ->
    client.newCall(
        GET(zipUrl, headers).newBuilder().range(byteRange).build()
    ).execute().body.source().buffer()
}

// 2. Find an entry by name and read its decompressed bytes.
val entry = directory.entries.first { it.name == "001.jpg" }
val imageBytes = readZipEntry(entry) { byteRange ->
    client.newCall(
        GET(zipUrl, headers).newBuilder().range(byteRange).build()
    ).execute().body.source().buffer()
}.buffer().readByteArray()
```

`readZipDirectory` resolves every entry's offset to an absolute file position and handles ZIP64 archives. `readZipEntry` fetches only the bytes for that one entry. Use this instead of downloading the full ZIP into a `ZipInputStream`, which forces the entire archive into memory.

#### Additional dependencies

If you find yourself needing additional functionality, you can add more dependencies to your `build.gradle.kts`
file. Many of [the dependencies](https://github.com/mihonapp/mihon/blob/main/app/build.gradle.kts)
from the app are exposed to extensions by default.

> [!NOTE]
> Several dependencies are already exposed to all extensions via Gradle's version catalog.
> To view which are available check the `gradle/libs.versions.toml` file.

Notice that we're using `compileOnly` instead of `implementation` if the app already contains it.
You could use `implementation` instead for a new dependency, or you prefer not to rely on whatever
the main app has at the expense of app size.

> [!TIP]
> Use `compileOnlyApi` (not `compileOnly`) when a dependency is provided by the app but also needs to be visible to consumers of your module (e.g. when building a theme or a library).

> [!IMPORTANT]
> Using `compileOnly` restricts you to versions that must be compatible with those used in
> [the latest stable version of the app](https://github.com/mihonapp/mihon/releases/latest).

### Extension main class

The class annotated with `@Source` and referenced by your `source {}` block(s). This class should implement `HttpSource`.

> [!NOTE]
> `className` is set to `ExtensionGenerated` automatically by the build system.

| Class              | Description                                                                                                                           |
|--------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| `SourceFactory`    | **Obsolete.** Used to expose multiple `Source`s manually. With `source {}` blocks, this is generated automatically.                   |
| `HttpSource`       | For online source, where requests are made using HTTP. Use this directly or extend a theme base class.                                |
| `ParsedHttpSource` | Deprecated, use `HttpSource` instead.                                                                                                 |

#### Main class key variables

> [!IMPORTANT]
> Since `source {}` blocks are required, these fields are generated and injected automatically. You can access them within your class (as they are part of the `HttpSource` contract), but **you must not declare or override them manually**.

| Field     | Description                                                                                                                                                     |
|-----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `name`    | Name displayed in the "Sources" tab in the app.                                                                                                                 |
| `baseUrl` | Base URL of the source without any trailing slashes.                                                                                                            |
| `lang`    | An ISO 639-1 compliant language code (two letters in lower case in most cases, but can also include the country/dialect part by using a simple dash character). |
| `id`      | Identifier of your source, automatically set in `HttpSource`. It should only be manually overridden if you need to copy an existing autogenerated ID.           |

### HTML and Image Processing

- **Parsing partial HTML:** If an API returns a JSON response containing an HTML string, use `Jsoup.parseBodyFragment(html, baseUrl)` instead of `Jsoup.parse(html)`. Passing the `baseUrl` ensures that `abs:href` and `absUrl()` correctly resolve relative links.

- **Formatting Chapter Numbers:** Do not write custom `DecimalFormat` logic solely to remove trailing zeros from float chapter numbers. Instead, use `.toString().removeSuffix(".0")`.

- **Generating Page lists:** The app ignores the `index` passed to the `Page` object, but you must ensure the list itself is sorted correctly according to the source. You can use Kotlin's `mapIndexed` to easily instantiate `Page` objects, or rely on the index provided by the source API if available:

  ```kotlin
  return document.select(".pages img").mapIndexed { index, img ->
      Page(index, imageUrl = img.attr("abs:src"))
  }
  ```

- **Memory-efficient Image Interceptors:** When implementing interceptors for descrambling, stitching, or decrypting images, avoid loading the entire image into a `ByteArray`, as this can cause `OutOfMemoryError` on low-end devices. Prefer stream-based processing:

  - **Read:** Use `response.body.byteStream()` with `BitmapFactory.decodeStream()` to decode images directly from the stream.
  - **Write:** Write the processed bitmap into an Okio `Buffer` via `output.outputStream()` and convert it using `asResponseBody(mediaType)`.
  - **Decryption:** Use Okio's `cipherSource` extension for stream-based decryption rather than decrypting a full byte array in memory.
  - Note: `readByteArray()` should generally be avoided here because it forces full in-memory buffering of the image. Streaming directly keeps memory usage lower and more stable.
  - Always wrap network responses in `response.use { ... }` to ensure the response body is properly closed and memory leaks are prevented.
  - If applicable, call `bitmap.recycle()` after use to free native memory early.

- **Do not manually check for Cloudflare:** Do not manually check for Cloudflare challenges (e.g., checking for "Just a moment..." text) in `parse` methods. The app handles this before calling the parser.
- **Prefer stable selectors:** Avoid relying on volatile auto-generated CSS class names (e.g., `styles_Card__jN8og`) or complex regex for parsing. Prefer stable structural selectors.
- **Use `ownText()` to avoid mutation:** To get text from an element without including text from its children, use `.ownText()`. This avoids the need to select and remove child elements (`.select().remove()`) or mutate the document.
- **Parse status using `.lowercase()`:** When comparing strings for status parsing (e.g., `contains("ongoing")`), prefer calling `.lowercase()` on the source string once instead of using `ignoreCase = true` on multiple `contains` checks.

### OkHttp and Network

- **Always pass `headers`:** Every `GET()` and `POST()` call must include `headers` (or a custom headers object). Omitting headers will send the request without the app's default User-Agent and other expected headers.
- **Referer header trailing slash:** When setting a `Referer` header pointing to the site root, always include a trailing slash: `.add("Referer", "$baseUrl/")`. This matches what browsers send and is required by some servers.
- **Static URLs don't need `HttpUrl.Builder`:** Use string interpolation directly for URLs with no dynamic query parameters. Only use `HttpUrl.Builder` (or `.toHttpUrl().newBuilder()`) when query parameters need URL-encoding or the URL is built conditionally.

  ```kotlin
  // Unnecessary builder for a static URL:
  val url = "$baseUrl/manga".toHttpUrl().newBuilder().build()
  // Prefer:
  return GET("$baseUrl/manga", headers)
  ```

- **GraphQL Queries:** If you are sending GraphQL requests, use Kotlin's raw multi-dollar string interpolation (`$$"""..."""`) for your queries. This prevents having to escape every JSON variable `$` symbol manually. For building the request and parsing the response, prefer the `graphQLPost` and `parseGraphQLAs` helpers in `keiyoushi.utils`.
- **Empty checks on `.text()`:** Because Jsoup's `.text()` automatically trims whitespace, you can use `.isNotEmpty()` instead of `.isNotBlank()` when checking for empty strings. The same applies to `.ownText()`. This also means you should not use `.trim()` with these functions.
- **Use `network.client` for Cloudflare:** When overriding the client for sources, simply use `override val client = network.client.newBuilder()...`.
- **Never use `Thread.sleep()`:** Do not use `Thread.sleep()` for rate limiting. Use the `keiyoushi.network.rateLimit` builder extension function on your `OkHttpClient.Builder` instead.
- **Avoid synchronous calls in `parse` methods:** Do not call `client.newCall(...).execute()` inside parsing methods like `pageListParse` or `chapterListParse`. Make the request part of the standard flow by overriding the corresponding request method (e.g., `pageListRequest`) or `fetchImageUrl`.
- **Pass `HttpUrl` directly:** The `GET()` and `POST()` helpers accept an `HttpUrl` object. Do not call `.toString()` on a built `HttpUrl` before passing it.
- **Use `HttpUrl` for URL manipulation:** When parsing or extracting parts of a URL, prefer using `HttpUrl` methods (like `pathSegments` property, `encodedPathSegments`, or `queryParameter("id")`) over manual string splitting (e.g., `.split("/")`) or regex. This ensures proper separation of concerns and protects against unexpected inputs-such as URL fragments or query parameters-without you needing to manually account for all edge cases.
- **Use `CookieInterceptor` for custom cookies:** When you need to inject custom cookies into requests, use the `lib-cookieinterceptor` dependency instead of manually adding `Cookie` headers. Manually setting the `Cookie` header overrides all cookies (including Cloudflare cookies set via WebView), breaking login and challenge solving.

### Extension call flow

#### Popular Manga

a.k.a. the Browse source entry point in the app (invoked by tapping on the source name).

- The app calls `fetchPopularManga` which should return a `MangasPage` containing the first batch of
  found `SManga` entries.
  - This method supports pagination. When user scrolls the manga list and more results must be fetched,
      the app calls it again with increasing `page` values (starting with `page=1`). This continues while
      `MangasPage.hasNextPage` is passed as `true` and `MangasPage.mangas` is not empty.
- To show the list properly, the app needs `url`, `title` and `thumbnail_url`. You **must** set them
  here. The rest of the fields could be filled later (refer to Manga Details below).

#### Latest Manga

a.k.a. the Latest source entry point in the app (invoked by tapping on the "Latest" button beside
the source name).

- Enabled if `supportsLatest` is `true` for a source
- Similar to popular manga, but should be fetching the latest entries from a source.

#### Manga Search

- When the user searches inside the app, `fetchSearchManga` will be called and the rest of the flow
  is similar to what happens with `fetchPopularManga`.
  - If search functionality is not available, return `Observable.just(MangasPage(emptyList(), false))`
- `getFilterList` will be called to get all filters and filter types.

##### Filters

The search flow has support for filters that can be added to a `FilterList` inside the `getFilterList`
method. When the user changes the filters' state, they will be passed to the `searchRequest`, and they
can be iterated to create the request (by getting the `filter.state` value, where the type varies
depending on the `Filter` used). You can check the [filter types available in Filter.kt](https://github.com/mihonapp/mihon/blob/main/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/Filter.kt)
and in the table below.

| Filter             | State type  | Description                                                                                                                                                              |
|--------------------|-------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Filter.Header`    | None        | A simple header. Useful for separating sections in the list or showing any note or warning to the user.                                                                  |
| `Filter.Separator` | None        | A line separator. Useful for visual distinction between sections.                                                                                                        |
| `Filter.Select<V>` | `Int`       | A select control, similar to HTML's `<select>`. Only one item can be selected, and the state is the index of the selected one.                                           |
| `Filter.Text`      | `String`    | A text control, similar to HTML's `<input type="text">`.                                                                                                                 |
| `Filter.CheckBox`  | `Boolean`   | A checkbox control, similar to HTML's `<input type="checkbox">`. The state is `true` if it's checked.                                                                    |
| `Filter.TriState`  | `Int`       | A enhanced checkbox control that supports an excluding state. The state can be compared with `STATE_IGNORE`, `STATE_INCLUDE` and `STATE_EXCLUDE` constants of the class. |
| `Filter.Group<V>`  | `List<V>`   | A group of filters (preferentially of the same type). The state will be a `List` with all the states.                                                                    |
| `Filter.Sort`      | `Selection` | A control for sorting, with support for the ordering. The state indicates which item index is selected and if the sorting is `ascending`.                                |

All control filters can have a default state set. It's usually recommended, if the source has filters
to make the initial state match the popular manga list. This way, when the user opens the filter sheet
the state accurately represents the currently displayed manga.

The `Filter` classes can also be extended, so you can create new custom filters like the `UriPartFilter`:

```kotlin
open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
    Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
```

#### Manga Details

- When a user taps on a manga, `fetchMangaDetails` and `fetchChapterList` are called and the results are cached.
  - A `SManga` entry is identified by its `url`.
- `fetchMangaDetails` is called to update a manga's details from when it was initialized earlier.
  - `SManga.initialized` tells the app whether to call `fetchMangaDetails`. If you are overriding
      `fetchMangaDetails`, ensure you set it to `true`.
  - `SManga.genre` is a string containing a list of all genres separated by `", "`.
  - `SManga.status` is an "enum" value. Refer to [the values in the `SManga` companion object](https://github.com/tachiyomiorg/extensions-lib/blob/8240b5cfecbd281bc737ac159ea7d4e5825ed3df/library/src/main/java/eu/kanade/tachiyomi/source/model/SManga.kt#L26).
  - During a backup, only `url` and `title` are stored. To restore the rest of the manga data, the
      app calls `fetchMangaDetails`, so all fields should be (re)filled if possible.
  - If a `SManga` is cached, `fetchMangaDetails` is only called when the user performs a manual
      update (Swipe-to-Refresh).
- `fetchChapterList` is called to display the chapter list.
  - **The list should be sorted descending by the source order**.
- `getMangaUrl` is called when the user taps "Open in WebView".
  - If the source uses an API to fetch the data, consider overriding this method to return the manga's
      absolute URL on the website instead.
  - It defaults to the URL provided to the request in `mangaDetailsRequest`.

#### Chapter

- `SChapter.date_upload` is the [UNIX Epoch time](https://en.wikipedia.org/wiki/Unix_time)
  **expressed in milliseconds**.
  - If you do not pass `SChapter.date_upload` and leave it at zero, the app will use the default date
      instead, but it is recommended to fill it if available.
  - To get the time in milliseconds from a date string, you can use a `SimpleDateFormat` as in
      the example below.

      ```kotlin
      import keiyoushi.utils.tryParse
  
      chapter.date_upload = dateFormat.tryParse(dateStr)
  
      private val dateFormat by lazy {
          SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
      }
      ```

      Ensure the `SimpleDateFormat` is a class constant or variable so it is not
      recreated for every chapter. If you need to parse or format dates in a manga description, create
      another instance since `SimpleDateFormat` is not thread-safe.

  - If parsing fails, return `0L` so the app uses the default date
      instead.
  - The app will overwrite the dates of existing chapters **UNLESS** `0L` is returned.
  - If the source only provides the manga's update date, assign it to the latest chapter only.

- `getChapterUrl` is called when the user taps "Open in WebView" in the reader.
  - If the source uses an API to fetch the data, consider overriding this method to return the
      chapter's absolute URL on the website instead.
  - It defaults to the URL provided to the request in `pageListRequest`.

#### Chapter Pages

- When a user opens a chapter, `fetchPageList` is called, returning a list of `Page`s.
  `pageListRequest` and `pageListParse` are used by `fetchPageList`.
- While a chapter is open in the reader or being downloaded, `fetchImageUrl` is called to get
  the URL for each page of the manga if `Page.imageUrl` is empty.
- If the source provides all `Page.imageUrl` values directly, you can fill them and leave `Page.url`
  empty. When set, `Page.url` and `Page.imageUrl` must be absolute URLs. `Page.url` may be empty if `imageUrl` is already filled.
- The list of `Page`s should be returned already sorted; the `index` field is ignored.
- If you need to pass additional data to the image fetcher, it is recommended to pass it as a URL fragment (e.g., `url + "#data"`). OkHttp does not send fragments to the server, so there is no need to strip it afterward.

### Misc notes

- **Use `asJsoup()`:** Instead of manually reading the response body and parsing it with Jsoup (`Jsoup.parse(response.body.string())`), use the app's built-in extension function: `response.asJsoup()` (requires `import eu.kanade.tachiyomi.util.asJsoup`).
- **Jsoup `.text()` is already trimmed:** Calling `element.text().trim()` is redundant because Jsoup automatically normalizes and trims whitespace. Just use `element.text()`.
- **Omit default `joinToString` separator:** The default separator for `joinToString` is already `", "`. Do not pass it explicitly. Use `joinToString { it.text() }` instead of `joinToString(", ") { it.text() }`, and `joinToString()` instead of `joinToString(", ")`.
- **Use named parameters for `Page`:** When instantiating `Page` objects, use the named parameter for the image URL: `Page(index, imageUrl = url)` instead of passing an empty string as the second argument (`Page(index, "", url)`).
- **Throw `UnsupportedOperationException`:** If a source uses an API and doesn't need to parse HTML for images, override `imageUrlParse(response: Response)` and throw `UnsupportedOperationException()` instead of returning an empty string. Also use this pattern for unused inherited methods.
- **Cache Regex instances:** Define `Regex` instances at the class level or in a `companion object` so they aren't recompiled on every method call.
- **Do not hardcode `User-Agent`:** Unless absolutely necessary (e.g., to bypass Cloudflare/protection, or to retrieve a specific mobile layout/different selectors), do not hardcode a specific `User-Agent`. Calling `super.headersBuilder()` already provides the app's default User-Agent.
- **Use `buildString { }`:** When building descriptions or dynamic strings, use Kotlin's `buildString { ... }` instead of manually instantiating a `StringBuilder()`.
- **Media Types:** `application/json` is intrinsically UTF-8. Avoid using `application/json; charset=utf-8`. Prefer helper functions like `toJsonRequestBody()` instead of manually specifying media types (e.g., `"application/json".toMediaType()`).
- **Use `getUrlWithoutDomain` carefully:** It can be useful when parsing target source URLs, but note a current issue with spaces-replace them with URL-encoded characters (e.g., `%20`).
- **Manga/chapter URLs:** Prefer storing just the ID or slug in `SManga.url` and `SChapter.url`. Storing the relative URL with `setUrlWithoutDomain()` is also acceptable. Avoid absolute URLs to make future domain migrations easier.
- **Follow `HttpSource` workflow:** Stick to the general workflow from this base class when possible; deviating may introduce unnecessary complexity.
- **Separate custom headers:** When adding custom headers to a request (e.g., for AJAX endpoints), avoid building them inline within the `GET()` or `POST()` call. Instead, assign the modified headers to a separate variable or define them as a class-level property. This improves readability and allows for reuse across multiple requests.
- **Do not override default `HttpSource` methods:** Avoid overriding methods like `mangaDetailsRequest` or `chapterListRequest` if they only replicate the default behavior `GET(baseUrl + manga.url, headers)`. Only override them if the source requires a different URL structure or custom headers for those specific requests.
- **Configurable sources:** By implementing `ConfigurableSource`, you can add settings backed by `SharedPreferences`.
- **Code organization:** For readability, group related methods together in your extension class (e.g., all popular manga methods, then all latest manga methods, then search methods, and so on). A logical ordering like Popular → Latest → Search → Details → Chapters → Pages → Filters → Utilities makes the class easier to navigate and maintain without needing explicit section header comments.
- **DTO extensions:** Move mapping extensions for DTOs (like `fun MyDto.toSManga()`) into the DTO file itself to keep the main source class clean.

### Advanced Extension features

#### Extension logic and app features

- **Mandatory fields:** A manga's `title` and `url` are **mandatory**. A chapter's `name` is also mandatory, though generic values like `"Chapter"` are acceptable for sources providing only a single chapter (e.g., gallery sources). Do not provide generic fallbacks like `"Untitled"`, `"Unknown"`, or empty strings if the site fails to provide a title or URL, as this breaks downloads and library management.
  Prefer failing loudly (e.g., throwing an exception or using `!!`) so broken selectors are detected early. Silent fallbacks or empty values can hide issues and make debugging harder. If a mandatory field is missing, it is better to throw an exception or skip the entry entirely.
- **Optional fields:** For all other fields, prefer safe calls (`?.`) and avoid the non-null assertion (`!!`). Missing data like thumbnails or descriptions should not crash the parsing process. Consider using Kotlin's `mapNotNull` when parsing lists of elements so that if a single item fails, the rest of the list can still load successfully.
- **Extension `name` field:** Do not add a language suffix or other qualifier to `name` (e.g., `"MySite EN"`). The app already groups sources by language.
- **`supportsLatest` convention:** If a source only has the latest listing, use that for the popular listing and set `supportsLatest = false`.
- **When to bump `source { versionId = ... }`:** The `versionId` field in the `source {}` block dictates how the source's ID is auto-computed. **Only bump it if the source's URL structure fundamentally changes** (e.g., old manga URLs no longer work and there is no way to create a redirect). Bumping this forces all users to re-migrate their bookmarks.
- **Self-hosted sources:** If you are adding a source for a self-hosted server (e.g., StashApp, Komga, Suwayomi), implement the `UnmeteredSource` interface in your class. This tells the app not to apply standard rate-limiting to the user's local server.
- **Preference listeners:** When implementing `ConfigurableSource`, you do not need to manually save values inside `setOnPreferenceChangeListener`. The Android preference framework saves the value to `SharedPreferences` automatically.
- **Update Strategy:** For gallery sources or sources where entries are completed upon upload, set `update_strategy = UpdateStrategy.ONLY_FETCH_ONCE` to prevent unnecessary update checks.
- **Preserving Source ID:** If you change a source's `name` or `lang`, its auto-generated `id` changes, disconnecting existing users' libraries. To prevent this, set `id` explicitly to the old value (found in `index.json`)- either in the `source {}` block or by overriding `id` in your class. See [Renaming existing sources](#renaming-existing-sources).
- **Avoid hardcoded host checks:** When checking URLs in deep links or search overrides, avoid hardcoding the host string (e.g., `queryUrl.host == "site.com"`). This breaks if mirrors are added. Prefer dynamically checking against the source's `baseUrl`.
- **Empty Lists vs. Exceptions:** If `pageListParse` or `chapterListParse` finds no items (e.g., a locked or empty chapter), return `emptyList()` instead of throwing a hardcoded exception. The app will display a localized error message.
- **Keep comments concise and preferably in English:** We recommend writing code comments and KDocs in English to make them accessible to all contributors. Additionally, avoid verbose, redundant, or AI-generated comments explaining obvious code. Keep the code clean and self-documenting.

#### Configurable Sources and Preferences

- **Mirror selection preferences:** When implementing a mirror selector, save the mirror's _index_ instead of the URL string. This allows code updates to change the mirror list, and users' settings will reflect those changes automatically.
- **Base URL getter:** When `baseUrl` is configurable via preferences, use a custom getter (e.g., `override val baseUrl: String get() = ...`) instead of `by lazy`. Using `by lazy` requires the user to restart the app for domain changes to take effect.
- **Preference migration for base URLs:** To handle default URL changes in updates, use the `getPreferences` inline migration block to update the stored preference if the hardcoded default URL changes.
- **Coerce mirror index:** When reading the mirror index from preferences, use `.coerceAtMost(mirrorUrls.size - 1)` to prevent `ArrayIndexOutOfBoundsException` if mirrors are removed in an update.

#### URL intent filter

Extensions can handle URLs from browsers or other apps by declaring deeplinks in `build.gradle.kts`. When a matching URL is opened on the device, Mihon launches and receives the URL as a search query.

Add one or more `deeplink {}` blocks inside the `keiyoushi {}` block:

```kotlin
keiyoushi {
    name = "My Source"
    // ...

    deeplink {
        host("example.com")
        path("/manga/..*")
        path("/chapter/..*")
    }
}
```

| DSL call              | Description                                                                                                                                                                                                                                                                  |
|-----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `host("example.com")` | A hostname to match. Call multiple times to register multiple hosts. If omitted, the host is derived from `baseUrl` automatically.                                                                                                                                           |
| `path("/manga/..*")`  | A path pattern in Android [`pathPattern`](https://developer.android.com/guide/topics/manifest/data-element#path) syntax. Call multiple times to match multiple paths. At least one `path()` call is required; a `deeplink {}` block with no paths produces no intent filter. |

Multiple `deeplink {}` blocks create independent intent filters, which is useful when different hosts or path groups must be handled separately:

```kotlin
deeplink {
    host("example.com")
    path("/manga/..*")
}

deeplink {
    host("cdn.example.com")
    path("/images/..*")
}
```

No `AndroidManifest.xml` or `UrlActivity.kt` is needed; they are generated and provided automatically by the build system.

If the extension uses a theme (via `theme = "<theme_name>"`), deeplinks defined in the theme's `build.gradle.kts` are automatically merged, so individual extensions using that theme do not need to repeat shared URL patterns.

Once deeplinks are declared, implement URL handling inside `fetchSearchManga`. When a deeplink is triggered, the app fires a search with the full URL as the query:

```kotlin
override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
    if (query.startsWith("https://")) {
        val url = query.toHttpUrlOrNull()
        if (url != null && url.host == baseUrl.toHttpUrl().host) {
            val typeIndex = url.pathSegments.indexOfFirst { it == "detail" || it == "view" }
            if (typeIndex != -1 && typeIndex + 1 < url.pathSize) {
                val id = url.pathSegments[typeIndex + 1]
                val manga = SManga.create().apply {
                    this@apply.url = "/Book?select=id,judul,cover&type=not.ilike.*novel*&id=eq.$id"
                    initialized = true
                }
                return fetchMangaDetails(manga)
                    .map {
                        it.url = manga.url
                        it.initialized = true
                        MangasPage(listOf(it), false)
                    }
            }

            throw Exception("Unsupported url")
        }
    }
    // normal search flow...
}
```

> [!NOTE]
> Avoid checking for hardcoded host strings (e.g., `url.host == "site.com"`). Prefer dynamically comparing against the source's `baseUrl` to maintain mirror support.

To test whether the URL intent filter is working as expected, use the `adb` command below:

```bash
adb shell am start -d "<your-link>" -a android.intent.action.VIEW
```

#### Update strategy

In some cases, titles in a source always have the same chapter list (i.e., they are immutable).
These do not need inclusion in global app updates. Excluding them saves network requests
and prevents unnecessary load on source servers. To change the update strategy of a `SManga`,
use the `update_strategy` field. Description of the current possible values follows:

- `UpdateStrategy.ALWAYS_UPDATE`: Titles marked as always update will be included in the library
  update unless excluded by additional restrictions.
- `UpdateStrategy.ONLY_FETCH_ONCE`: Titles marked as only fetch once are automatically skipped
  during library updates. This is useful for cases where the series is known to be finished and has
  only a single chapter, for example.

If not set, it defaults to `ALWAYS_UPDATE`.

#### Renaming existing sources

If existing sources change their names on the website, you must explicitly set the `id` to the previous value to reflect these changes correctly. Otherwise, it will change based on the new `name` value, forcing users to re-migrate to the source.

To get the current `id` value before a name change, search the source name in the [repository JSON file](https://github.com/keiyoushi/extensions/blob/repo/index.json)
under the `sources` attribute of the extension.

**If you are using `source {}` blocks**, set `id` directly in the block:

```kotlin
source {
    name = "New Name"  // or lang = "xx" if lang is what changed
    lang = "en"
    baseUrl = "https://example.com"
    id = 1234567890123456789L // Replace with the actual old source ID
}
```

The class name and the `name` attribute value can then be changed. Also, update the extension name and class name in the individual Gradle file.

> [!IMPORTANT]
> The package name **must** remain the same (even if it uses the old name); otherwise, users will not
> receive the extension update when published in the repository.

The `id` also must be explicitly set to the old value if you change the `lang` attribute.

> [!NOTE]
> If the source has also changed its theme, you can simply change
> the `name` field in the source class and the Gradle file. By doing so,
> a new `id` is generated and users will be forced to migrate.

## Multi-source themes

The `lib-multisrc` directory houses source code useful when multiple source
sites use the same site generator tool (usually a CMS). Their similarity prompts code reuse through inheritance or composition, referred to here as a **theme**.

Themes are provided as libraries within `lib-multisrc`. You can apply a theme to an extension by specifying the `theme` property in its `build.gradle.kts` file.

### Creating a new theme

To create a new theme, set up a new module inside the `lib-multisrc` directory. The structure is similar to a regular extension, but it acts as a base library that other extensions can depend on.

#### Theme directory structure

```console
$ tree lib-multisrc/<theme_name>/
lib-multisrc/<theme_name>/
├── build.gradle.kts
└── src
    └── eu
        └── kanade
            └── tachiyomi
                └── multisrc
                    └── <theme_name>
                        └── <ThemeName>.kt
```

`<theme_name>` should be adapted from the CMS or theme name and can only contain lowercase ASCII letters and digits. Your theme code must be placed in the package `eu.kanade.tachiyomi.multisrc.<theme_name>`.

#### Theme build.gradle.kts

Ensure that your new theme's `build.gradle.kts` file follows this structure:

```kotlin
plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 1
    libVersion = "1.4"
}
```

If the CMS generates URLs with a consistent structure shared by all sites built on it, you can declare deeplinks here as well. Every extension using this theme inherits them automatically:

```kotlin
keiyoushi {
    baseVersionCode = 1
    libVersion = "1.4"

    deeplink {
        path("/manga/..*")
        path("/chapter/..*")
    }
}
```

When no `host()` is specified in a theme `deeplink {}` block, the host is resolved at build time from each individual extension's `baseUrl`, so the same path patterns apply to every site without hardcoding hostnames in the theme.

| Field             | Description                                                                                                                                                           |
|-------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `baseVersionCode` | The theme's base version code. This must be a positive integer and **incremented** whenever a change is made to the theme implementation that affects the extensions. |
| `libVersion`      | The extension library version. Always set to `"1.4"`.                                                                                                                 |
| `deeplink {}`     | Declares URL deeplink patterns inherited by all extensions using this theme. See [URL intent filter](#url-intent-filter).                                             |

#### Theme main class

The theme's main class (e.g., `<ThemeName>.kt`) contains the default implementation for the source sites. It should be declared as an `abstract class` extending `HttpSource`, allowing individual extensions to inherit and override its properties and methods.

```kotlin
package eu.kanade.tachiyomi.multisrc.example // Replace 'example' with your theme name

import eu.kanade.tachiyomi.source.online.HttpSource

abstract class ExampleTheme : HttpSource() {

    // name, lang, and baseUrl are inherited from HttpSource. They are automatically
    // overridden and injected by the KSP generator via each extension's `source {}`
    // block. Do not declare them in the constructor.

    // Theme default implementation...

}
```

### Using a Theme

To use a theme in your extension, follow the regular extension creation steps and configure `theme` in your `build.gradle.kts`:

```kotlin
// build.gradle.kts
keiyoushi {
    name = "Example" // Replace with your actual source name
    theme = "example" // Replace with your actual theme name
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://example.com"
    }
}
```

```kotlin
// MySource.kt
package eu.kanade.tachiyomi.extension.en.mysource

import keiyoushi.annotation.Source

@Source
abstract class MySource : ExampleTheme() { // Replace 'ExampleTheme' with your theme name
    // name, lang, id, baseUrl injected automatically
    // override theme defaults as needed
}
```

The final extension version code is automatically calculated during the build as `theme.baseVersionCode + versionCode`.

Because themes are provided as libraries, your extension's main class inherits directly from the theme's base class.

Site-specific overrides, custom functions, or custom icons are implemented directly in your extension's module (`src/<lang>/<mysourcename>`) by overriding the inherited theme properties and functions.

## Running

For local development, use the following run configuration to launch the app directly into the Browse panel.

![Android Studio: Run/Debug Configurations](https://i.imgur.com/6s2dvax.png)

Copy the following into `Launch Flags` for the Debug build of Mihon:

```bash
-W -S -n app.mihon.dev/eu.kanade.tachiyomi.ui.main.MainActivity -a eu.kanade.tachiyomi.SHOW_CATALOGUES
```

For other builds, replace `app.mihon.dev` with the corresponding package IDs:

- Release build: `app.mihon`
- Preview build: `app.mihon.debug`

If the extension builds and runs successfully, the code changes should be ready to test in your local app.

> [!IMPORTANT]
> If you are deploying to Android 11 or higher, enable the `Always install with package manager` option in the run configurations. Otherwise, you might face issues such as Android Studio running an older version of the extension without your modifications.

## Debugging

### Android Debugger

> [!NOTE]
> It is generally recommended to rely on logging instead of the Android Debugger. Using standard logs (like `Log.d` or viewing OkHttp logs) is typically much faster, easier to set up, and sufficient for debugging web scraping logic.

> [!IMPORTANT]
> If you did not **build the main app** from source with **debug enabled** and are using a release or beta APK, you **need a rooted device**.
> If you are using an **emulator**, ensure you choose a profile **without Google Play**.

Follow the steps above for building and running locally if you haven't already. Debugging will not work if you did not follow those steps.

You can leverage the Android Debugger to add breakpoints and step through your extension while debugging.

You _cannot_ simply use Android Studio's `Debug 'module.name'`; this will likely result in an
error while launching.

Instead, once you've built and installed your extension on the target device, use
`Attach Debugger to Android Process` to start debugging the app.

Inside the `Attach Debugger to Android Process` window, once the app is running on your device and `Show all processes` is checked, you should be able to select `app.mihon.dev` and press OK.

![Android Studio: Choose Process](https://i.imgur.com/SUhdB52.png)

### Logs

You can also elect to rely on logs printed from your extension, which
show up in the [`Logcat`](https://developer.android.com/studio/debug/am-logcat) panel of Android Studio.

### Inspecting network calls

One of the easiest ways to inspect network issues (such as HTTP errors 404, 429, no chapter found, etc.)
is to use the [`Logcat`](https://developer.android.com/studio/debug/am-logcat) panel of Android Studio
and filter by the `OkHttpClient` tag.

To check the calls made by OkHttp, you must enable verbose logging in the app; it is not enabled by default. To enable it, go to More → Settings → Advanced → Verbose logging. Afterward, restart the app.

Inspecting the Logcat allows you to see the call flow and is sufficient in most
cases. Alternatively, you can use an external tool like `mitmproxy`.
For that, refer to the subsequent sections.

On newer Android Studio versions, you can use the built-in Network Inspector inside the
App Inspection tool window. This feature provides a GUI to inspect the requests made in the app.

To use it, follow the [official documentation](https://developer.android.com/studio/debug/network-profiler)
and select the app's package name in the process list.

### Using external network inspecting tools

If you want a deeper look into the network flow, such as inspecting the request and response bodies,
you can use an external tool like `mitmproxy`.

#### Set up your proxy server

We are going to use [mitmproxy](https://mitmproxy.org/), but you can replace it with any other Web
Debugger (e.g., Charles, Burp Suite, Fiddler). To install and execute, follow the commands below.

> [!WARNING]
> Do NOT use `sudo pip` to install `mitmproxy`. Follow the [official installation methods](https://docs.mitmproxy.org/stable/overview-installation/).

After installing it, you can run `mitmweb` to open the web interface.

Alternatively, use the Docker image:

```bash
$ docker run --rm -it -p 8080:8080 \
    -p 127.0.0.1:8081:8081 \
    --web-host 0.0.0.0 \
    mitmproxy/mitmproxy mitmweb
```

After installing and running, open your browser and navigate to <http://127.0.0.1:8081>.

#### OkHttp proxy setup

Since most manga sources use HTTPS, we must disable SSL verification to use the web debugger.

> [!CAUTION]
> The following code disables certificate and hostname verification. Use it **only** in a local debug build, **never** submit it as production source code, and **remove it** before opening a Pull Request.

For that, add this code inside your source class:

```kotlin
package eu.kanade.tachiyomi.extension.en.mysource

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Source
abstract class MySource : HttpSource() {
    private fun OkHttpClient.Builder.ignoreAllSSLErrors(): OkHttpClient.Builder {
        val naiveTrustManager = @SuppressLint("CustomX509TrustManager")
        object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
        }

        val insecureSocketFactory = SSLContext.getInstance("TLSv1.2").apply {
            val trustAllCerts = arrayOf<TrustManager>(naiveTrustManager)
            init(null, trustAllCerts, SecureRandom())
        }.socketFactory

        sslSocketFactory(insecureSocketFactory, naiveTrustManager)
        hostnameVerifier { _, _ -> true }
        return this
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .ignoreAllSSLErrors()
        .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("10.0.2.2", 8080)))
        .build()
}
```

Note: `10.0.2.2` is usually the address of your loopback interface in the Android emulator. If
the app tells you that it's unable to connect to 10.0.2.2:8080, you will likely need to change it
(the same if you are using a hardware device).

If all went well, you should see all requests and responses made by the source in the web interface
of `mitmweb`.

## Building

APKs can be created in Android Studio via `Build > Build Bundle(s) / APK(s) > Build APK(s)` or
`Build > Generate Signed Bundle / APK`.

If you decide to build the APK from the command line, use the following
command:

```console
// For a single apk, use this command
$ ./gradlew src:<lang>:<source>:assembleDebug
```

## Submitting the changes

When you feel confident about your changes, submit a new Pull Request for review. We encourage following a [GitHub Standard Fork & Pull Request Workflow](https://gist.github.com/Chaser324/ce0505fbed06b947d962); avoid committing directly to `main` and always create a new branch for your changes.

If you prefer using Git GUI-based tools, refer to [this guide](https://learntodroid.com/how-to-use-git-and-github-in-android-studio/)
about Git integration in Android Studio. Specifically, check the "How to Contribute to an Existing
Git Repository in Android Studio" section.

> [!IMPORTANT]
> Ensure you have generated the extension icon using the Icon Generator tool in the [Tools](#tools)
> section. The icon **must follow the pattern** adopted by all extensions: a square with rounded
> corners. Remove the generated `web_hi_res_512.png`.

Please **test your changes by compiling through Android Studio** before submitting. Untested PRs will not be merged. Also, ensure you follow the PR checklist in the PR body field when creating a new PR; it is provided below for reference.

### Pull Request checklist

- Updated `versionCode` value in `build.gradle.kts`
- Updated `baseVersionCode` in `build.gradle.kts` (if updated multisrc theme code)
- Referenced all related issues in the PR body (e.g. "Closes #xyz")
- Set the `contentWarning` configuration in `build.gradle.kts` appropriately
- Have not changed source names
- Have explicitly kept the `id` if a source's name or language were changed
- Have tested the modifications by compiling and running the extension through Android Studio
- Have removed `web_hi_res_512.png` when adding a new extension
- This PR is AI-assisted, I have reviewed the changes manually and confirmed they are not slop
