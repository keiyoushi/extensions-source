# Contributing

This guide has some instructions and tips on how to create a new Keiyoushi extension. Please **read
it carefully** if you're a new contributor or don't have any experience on the required languages
and knowledges.

This guide is not definitive and it's being updated over time. If you find any issues in it, feel
free to report it through a [Meta Issue](https://github.com/keiyoushi/extensions-source/issues/new?assignees=&labels=Meta+request&template=06_request_meta.yml)
or fixing it directly by submitting a Pull Request.

## Table of Contents

- [Contributing](#contributing)
  - [Table of Contents](#table-of-contents)
  - [Prerequisites](#prerequisites)
    - [Tools](#tools)
    - [Cloning the repository](#cloning-the-repository)
  - [Getting help](#getting-help)
  - [Writing an extension](#writing-an-extension)
    - [Setting up a new Gradle module](#setting-up-a-new-gradle-module)
    - [Loading a subset of Gradle modules](#loading-a-subset-of-gradle-modules)
      - [Extension file structure](#extension-file-structure)
      - [AndroidManifest.xml (optional)](#androidmanifestxml-optional)
      - [build.gradle](#buildgradle)
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
        - [Protobuf parsing and serialization — `parseAsProto` / `toRequestBodyProto`](#protobuf-parsing-and-serialization--parseasproto--torequestbodyproto)
        - [Date parsing - `tryParse`](#date-parsing---tryparse)
        - [Filter helpers - `firstInstance` / `firstInstanceOrNull`](#filter-helpers---firstinstance--firstinstanceornull)
        - [Next.js data extraction - `extractNextJs` / `extractNextJsRsc`](#nextjs-data-extraction---extractnextjs--extractnextjsrsc)
        - [Extracting URLs - `setUrlWithoutDomain` + `absUrl`](#extracting-urls---seturlwithoutdomain--absurl)
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
      - [Setup your proxy server](#setup-your-proxy-server)
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

    There are two modes of pattern matching. The default is cone (🔺) mode.
    Cone mode enables significantly faster pattern matching for big monorepos
    and the sparse index feature to make Git commands more responsive.
    In this mode, you can only filter by file path, which is less flexible
    and might require more work when the project structure changes.

    You can skip this code block to use legacy mode if you want easier filters.
    It won't be much slower as the repo doesn't have that many files.

    To enable cone mode together with sparse index, follow these steps:

    ```bash
    git sparse-checkout set --cone --sparse-index
    # add project folders
    git sparse-checkout add buildSrc core gradle lib lib-multisrc utils
    # add a single source
    git sparse-checkout add src/<lang>/<source>
    ```

    To remove a source, open `.git/info/sparse-checkout` and delete the exact
    lines you typed when adding it. Don't touch the other auto-generated lines
    unless you fully understand how cone mode works, or you might break it.

    To use the legacy non-cone mode, follow these steps:

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

- Join [the Discord server](https://discord.gg/3FbCpdKbdY) for online help and to ask questions while
developing your extension. When doing so, please ask them in the `#programming` channel.
- There are some features and tricks that are not explored in this document. Refer to existing
extension code for examples.

## Writing an extension

The quickest way to get started is to copy an existing extension's folder structure and renaming it
as needed. We also recommend reading through a few existing extensions' code before you start.

### Setting up a new Gradle module

Each extension should reside in `src/<lang>/<mysourcename>`. Use `all` as `<lang>` if your target
source supports multiple languages or if it could support multiple sources.

The `<lang>` used in the folder inside `src` should be the major `language` part. For example, if
you will be creating a `pt-BR` source, use `<lang>` here as `pt` only. Inside the source class, use
the full locale string instead.

### Loading a subset of Gradle modules

By default, all individual and multisrc extensions are loaded for local development.
This may be inconvenient and can drastically slow down your system when working on a single extension.

To adjust which modules are loaded, make adjustments to the `settings.gradle.kts` file as needed. You can specify the single extension you want to work on in the `load individual extension` function. This helps avoid loading unnecessary modules, making the build process more efficient and preventing your CPU from being overworked.

#### Extension file structure

The simplest extension structure looks like this:

```console
$ tree src/<lang>/<mysourcename>/
src/<lang>/<mysourcename>/
├── AndroidManifest.xml (optional)
├── build.gradle
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
                            ├── <Filters>.kt (optional)
                            └── <UrlActivity>.kt (optional)

```

`<lang>` should be an ISO 639-1 compliant language code (two letters or `all`). `<mysourcename>`
should be adapted from the site name, and can only contain lowercase ASCII letters and digits.
Your extension code must be placed in the package `eu.kanade.tachiyomi.extension.<lang>.<mysourcename>`.

> [!TIP]
> Additional files in the extension package (like `Dto.kt`, `Filters.kt`, `UrlActivity.kt`)
> should NOT repeat the extension name (e.g. use `Dto.kt` instead of `MySourceNameDto.kt`).
> Note: While older extensions might use the repeated name pattern, avoiding it is a newly enforced convention to maintain consistency across the repository.

#### AndroidManifest.xml (optional)

You only need to create this file if you want to add deep linking to your extension.
See [URL intent filter](#url-intent-filter) for more information.

#### build.gradle

Make sure that your new extension's `build.gradle` file follows the following structure:

```groovy
ext {
    extName = '<My source name>'
    extClass = '.<MySourceName>'
    extVersionCode = 1
    isNsfw = true
}

apply from: "$rootDir/common.gradle"
```

| Field            | Description                                                                                                                                                                            |
|------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `extName`        | The name of the extension. Should be romanized if site name is not in English.                                                                                                         |
| `extClass`       | Points to the class that implements `Source`. You can use a relative path starting with a dot (the package name is the base path). This is used to find and instantiate the source(s). |
| `extVersionCode` | The extension version code. This must be a positive integer and incremented with any change to the code.                                                                               |
| `isNsfw`         | (Optional, defaults to `false`) Flag to indicate that a source contains NSFW content.                                                                                                  |

The extension's version name is generated automatically by concatenating `1.4` and `extVersionCode`.
With the example used above, the version would be `1.4.1`.

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

| Module                                                                                                    | Description                                                          |
|-----------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|
| [`lib-cookieinterceptor`](https://github.com/keiyoushi/extensions-source/tree/main/lib/cookieinterceptor) | Injects cookies into OkHttp requests for a given domain              |
| [`lib-cryptoaes`](https://github.com/keiyoushi/extensions-source/tree/main/lib/cryptoaes)                 | AES-CBC decryption compatible with CryptoJS; JSFuck deobfuscation    |
| [`lib-randomua`](https://github.com/keiyoushi/extensions-source/tree/main/lib/randomua)                   | Fetches and rotates real-world User-Agent strings                    |
| [`lib-synchrony`](https://github.com/keiyoushi/extensions-source/tree/main/lib/synchrony)                 | JavaScript deobfuscation via the Synchrony engine (QuickJS sandbox)  |
| [`lib-textinterceptor`](https://github.com/keiyoushi/extensions-source/tree/main/lib/textinterceptor)     | Renders plain text or HTML as a PNG image page                       |
| [`lib-unpacker`](https://github.com/keiyoushi/extensions-source/tree/main/lib/unpacker)                   | Unpacks Dean Edwards-packed JavaScript; substring extraction helpers |

> [!NOTE]
> The table above highlights the most commonly used libraries. Check the `lib/` directory for the full list of available modules and their specific READMEs.

#### Adding a lib dependency

Declare the module in your extension's `build.gradle`:

```groovy
dependencies {
    implementation(project(':lib:<name>'))
}
```

For example:

```groovy
dependencies {
    implementation(project(':lib:dataimage'))
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

The `build.gradle.kts` must apply the `lib-android` plugin:

```kotlin
plugins {
    id("lib-android")
}
```

If your lib depends on another lib, declare it in the same file:

```kotlin
plugins {
    id("lib-android")
}

dependencies {
    implementation(project(":lib:<other-lib>"))
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

##### Protobuf parsing and serialization — `parseAsProto` / `toRequestBodyProto`

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
````

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

**Do not** write manual try/catch blocks or null-guards around `SimpleDateFormat.parse()` -
`tryParse` handles both. Also, always declare your `SimpleDateFormat` as a class-level or
file-level `val` so it is not reconstructed for every chapter.

##### Filter helpers - `firstInstance` / `firstInstanceOrNull`

Use these instead of `filterIsInstance<T>().first()` / `filterIsInstance<T>().firstOrNull()`.

```kotlin
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
```

**SharedPreferences - `getPreferences` / `getPreferencesLazy`**

Use these instead of accessing `Injekt` manually.

```kotlin
import keiyoushi.utils.getPreferences
import keiyoushi.utils.getPreferencesLazy

// Eager:
private val preferences = getPreferences()

// Lazy (recommended for most cases):
private val preferences by getPreferencesLazy()
```

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
request header and use `extractNextJsRsc` on the response body string.
See [#14266](https://github.com/keiyoushi/extensions-source/pull/14266) and
[#14446](https://github.com/keiyoushi/extensions-source/pull/14446) for real-world usage.

##### Extracting URLs - `setUrlWithoutDomain` + `absUrl`

When extracting URLs from HTML, prefer `element.absUrl("href")` or `element.attr("abs:href")` over manually concatenating `baseUrl` + `path`. Combined with `setUrlWithoutDomain()`, this safely handles both absolute and relative links.

#### Additional dependencies

If you find yourself needing additional functionality, you can add more dependencies to your `build.gradle`
file. Many of [the dependencies](https://github.com/mihonapp/mihon/blob/main/app/build.gradle.kts)
from the app are exposed to extensions by default.

> [!NOTE]
> Several dependencies are already exposed to all extensions via Gradle's version catalog.
> To view which are available check the `gradle/libs.versions.toml` file.

Notice that we're using `compileOnly` instead of `implementation` if the app already contains it.
You could use `implementation` instead for a new dependency, or you prefer not to rely on whatever
the main app has at the expense of app size.

> [!IMPORTANT]
> Using `compileOnly` restricts you to versions that must be compatible with those used in
> [the latest stable version of the app](https://github.com/mihonapp/mihon/releases/latest).

### Extension main class

The class which is referenced and defined by `extClass` in `build.gradle`. This class should implement
either `SourceFactory` or `HttpSource`.

| Class              | Description                                                                                                                      |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------|
| `SourceFactory`    | Used to expose multiple `Source`s. Use this in case of a source that supports multiple languages or mirrors of the same website. |
| `HttpSource`       | For online source, where requests are made using HTTP.                                                                           |
| `ParsedHttpSource` | Deprecated, use `HttpSource` instead.                                                                                            |

#### Main class key variables

| Field     | Description                                                                                                                                                     |
|-----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `name`    | Name displayed in the "Sources" tab in the app.                                                                                                                 |
| `baseUrl` | Base URL of the source without any trailing slashes.                                                                                                            |
| `lang`    | An ISO 639-1 compliant language code (two letters in lower case in most cases, but can also include the country/dialect part by using a simple dash character). |
| `id`      | Identifier of your source, automatically set in `HttpSource`. It should only be manually overridden if you need to copy an existing autogenerated ID.           |

### HTML and Image Processing

- **Parsing partial HTML:** If an API returns a JSON response containing an HTML string, use `Jsoup.parseBodyFragment(html, baseUrl)` instead of `Jsoup.parse(html)`. Passing the `baseUrl` ensures that `abs:href` and `absUrl()` can correctly resolve relative links.

- **Formatting Chapter Numbers:** Do not write custom `DecimalFormat` logic just to remove trailing zeros from float chapter numbers. Simply use `.toString().removeSuffix(".0")`.

- **Generating Page lists:** The app ignores the `index` passed to the `Page` object, but you must ensure the list itself is sorted correctly according to the source. You can use Kotlin's `mapIndexed` to easily instantiate `Page` objects, or rely on the index provided by the source API if available:

    ```kotlin
    return document.select(".pages img").mapIndexed { index, img ->
        Page(index, imageUrl = img.attr("abs:src"))
    }
    ```

- **Memory-efficient Image Interceptors:** When implementing interceptors for descrambling, stitching, or decrypting images, avoid loading the entire image into a `ByteArray`, as this can cause `OutOfMemoryError` on low-end devices. Prefer stream-based processing instead:

  - **Read:** Use `response.body.byteStream()` with `BitmapFactory.decodeStream()` to decode images directly from the stream.
  - **Write:** Write the processed bitmap into an Okio `Buffer` via `output.outputStream()` and convert it using `asResponseBody(mediaType)`.
  - **Decryption:** Use Okio's `cipherSource` extension for stream-based decryption rather than decrypting a full byte array in memory.
  - Note: `readByteArray()` should generally be avoided here because it forces full in-memory buffering of the image. Streaming directly keeps memory usage lower and more stable.
  - Always wrap network responses in `response.use { ... }` to ensure the response body is properly closed and to prevent memory leaks.
  - If applicable, call `bitmap.recycle()` after you're done with it to free native memory early.

### OkHttp and Network

- **GraphQL Queries:** If you are sending GraphQL requests, use Kotlin's raw multi-dollar string interpolation (`$$"""..."""`) for your queries. This prevents having to escape every JSON variable `$` symbol manually.

- **Empty checks on `.text()`:** Because Jsoup's `.text()` automatically trims whitespace, you can use `.isNotEmpty()` instead of `.isNotBlank()` when checking for empty strings.

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

- When user taps on a manga, `getMangaDetails` and `getChapterList` will be called and the results
will be cached.
  - A `SManga` entry is identified by it's `url`.
- `getMangaDetails` is called to update a manga's details from when it was initialized earlier.
  - `SManga.initialized` tells the app if it should call `getMangaDetails`. If you are overriding
  `getMangaDetails`, make sure to pass it as `true`.
  - `SManga.genre` is a string containing list of all genres separated with `", "`.
  - `SManga.status` is an "enum" value. Refer to [the values in the `SManga` companion object](https://github.com/tachiyomiorg/extensions-lib/blob/8240b5cfecbd281bc737ac159ea7d4e5825ed3df/library/src/main/java/eu/kanade/tachiyomi/source/model/SManga.kt#L26).
  - During a backup, only `url` and `title` are stored. To restore the rest of the manga data, the
  app calls `getMangaDetails`, so all fields should be (re)filled in if possible.
  - If a `SManga` is cached, `getMangaDetails` will be only called when the user does a manual
  update (Swipe-to-Refresh).
- `getChapterList` is called to display the chapter list.
  - **The list should be sorted descending by the source order**.
- `getMangaUrl` is called when the user taps "Open in WebView".
  - If the source uses an API to fetch the data, consider overriding this method to return the manga
  absolute URL in the website instead.
  - It defaults to the URL provided to the request in `mangaDetailsRequest`.

#### Chapter

- `SChapter.date_upload` is the [UNIX Epoch time](https://en.wikipedia.org/wiki/Unix_time)
**expressed in milliseconds**.
  - If you don't pass `SChapter.date_upload` and leave it zero, the app will use the default date
  instead, but it's recommended to always fill it if it's available.
  - To get the time in milliseconds from a date string, you can use a `SimpleDateFormat` like in
  the example below.

    ```kotlin
    import keiyoushi.utils.tryParse

    chapter.date_upload = dateFormat.tryParse(dateStr)

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    }
    ```

    Make sure you make the `SimpleDateFormat` a class constant or variable so it doesn't get
  recreated for every chapter. If you need to parse or format dates in manga description, create
  another instance since `SimpleDateFormat` is not thread-safe.
  - If the parsing has any problems, make sure to return `0L` so the app will use the default date
  instead.
  - The app will overwrite dates of existing old chapters **UNLESS** `0L` is returned.
  - If the source only provides the manga's updated date, assign it to the latest chapter only.
- `getChapterUrl` is called when the user taps "Open in WebView" in the reader.
  - If the source uses an API to fetch the data, consider overriding this method to return the
  chapter absolute URL in the website instead.
  - It defaults to the URL provided to the request in `pageListRequest`.

#### Chapter Pages

- When user opens a chapter, `getPageList` will be called and it will return a list of `Page`s.
- While a chapter is open in the reader or is being downloaded, `fetchImageUrl` will be called to get
the URL for each page of the manga if `Page.imageUrl` is empty.
- If the source provides all the `Page.imageUrl`s directly, you can fill them and leave `Page.url`
empty, so the app will skip the `fetchImageUrl` step and directly call `fetchImage`.
- The `Page.url` and `Page.imageUrl` attributes **should be set as absolute URLs**.
- The list of `Page`s should be returned already sorted, the `index` field is ignored.
- If you need to pass additional data to the image fetcher, it is recommended to pass it as a URL fragment (e.g. `url + "#data"`). OkHttp does not send fragments to the server, so there is no need to strip it out afterwards.

### Misc notes

- **Use `asJsoup()`:** Instead of manually reading the response body and parsing it with Jsoup (`Jsoup.parse(response.body.string())`), use the app's built-in extension function: `response.asJsoup()` (requires `eu.kanade.tachiyomi.util.asJsoup`).
- **Jsoup `.text()` is already trimmed:** Calling `element.text().trim()` is redundant because Jsoup automatically normalizes and trims whitespace. Just use `element.text()`.
- **Use named parameters for `Page`:** When instantiating `Page` objects, use the named parameter for the image URL: `Page(index, imageUrl = url)` instead of passing an empty string as the second argument (`Page(index, "", url)`).
- **Throw `UnsupportedOperationException`:** If a source uses an API and doesn't need to parse HTML for images, override `imageUrlParse(response: Response)` and throw `UnsupportedOperationException()` instead of returning an empty string. Also use this pattern for unused inherited methods.
- **Cache Regex instances:** Define `Regex` instances at the class level or in a `companion object` so they aren't recompiled on every method call.
- **Do not hardcode `User-Agent`:** Unless absolutely necessary (e.g., to bypass Cloudflare/protection, or to retrieve a specific mobile layout/different selectors), do not hardcode a specific `User-Agent`. Calling `super.headersBuilder()` already provides the app's default User-Agent.
- **Use `buildString { }`:** When building descriptions or dynamic strings, use Kotlin's `buildString { ... }` instead of manually instantiating a `StringBuilder()`.
- **Media Types:** `application/json` is intrinsically UTF-8. Avoid using `application/json; charset=utf-8`. Prefer helper functions like `toJsonRequestBody()` instead of manually specifying media types (e.g., `"application/json".toMediaType()`).
- **Use `getUrlWithoutDomain` carefully:** It can be useful when parsing target source URLs, but note a current issue with spaces-replace them with URL-encoded characters (e.g., `%20`).
- **Follow `HttpSource` workflow:** Stick to the general workflow from this base class when possible; deviating may introduce unnecessary complexity.
- **Separate custom headers:** When adding custom headers to a request (e.g., for AJAX endpoints), avoid building them inline within the `GET()` or `POST()` call. Instead, assign the modified headers to a separate variable or define them as a class-level property. This improves readability and allows for reuse across multiple requests.
- **Do not override default `HttpSource` methods:** Avoid overriding methods like `mangaDetailsRequest` or `chapterListRequest` if they only replicate the default behavior (`GET(baseUrl + manga.url, headers`). Only override them if the source requires a different URL structure or custom headers for those specific requests.
- **Configurable sources:** By implementing `ConfigurableSource`, you can add settings backed by `SharedPreferences`.
- **Code organization:** For readability, group related methods together in your extension class (e.g., all popular manga methods, then all latest manga methods, then search methods, and so on). A logical ordering like Popular → Latest → Search → Details → Chapters → Pages → Filters → Utilities makes the class easier to navigate and maintain without needing explicit section header comments.

### Advanced Extension features

#### Extension logic and app features

- **Mandatory fields:** A manga's `title` and `url` are **mandatory**. A chapter's `name` is also mandatory, though generic values like `"Chapter"` are acceptable for sources that only provide a single chapter (e.g., gallery sources). Do not provide generic fallbacks like `"Untitled"`, `"Unknown"`, or empty strings if the site fails to provide a manga's title or URL, as this breaks downloads and library management.
  Prefer failing loudly (e.g., throwing an exception) so broken selectors are detected early. Silent fallbacks or empty values can hide issues and make debugging harder. If a mandatory field is missing, it is better to throw or skip the entry entirely.
- **Optional fields:** For all other fields, prefer safe calls (`?.`) and avoid using the non-null assertion (`!!`). Missing data like thumbnails or descriptions should not crash the entire parsing process. Consider using Kotlin's `mapNotNull` when parsing lists of elements so that if a single item fails, the rest of the list can still be loaded successfully.
- **When to bump `versionId`:** The `versionId` property dictates how the app tracks the source. **Only override and bump `versionId` if the source's URL structure fundamentally changes** (e.g., old manga URLs no longer work and there is no way to create a redirect). Bumping this forces all users to re-migrate their bookmarks.
- **Self-hosted sources:** If you are adding a source for a self-hosted server (e.g., StashApp, Komga, Suwayomi), make your class implement the `UnmeteredSource` interface. This tells the app not to apply standard rate-limiting to the user's own local server.
- **Preference listeners:** When implementing `ConfigurableSource`, you do not need to manually save values inside `setOnPreferenceChangeListener`. The Android preference framework saves the value to `SharedPreferences` automatically.

#### URL intent filter

Extensions can define a URL pattern so that these URLs can be opened in Mihon.

To do this, you need two files:

- `AndroidManifest.xml` which must be placed in the root directory of your extension (Example: `src/id/riztranslation/AndroidManifest.xml`)
- `UrlActivity.kt` which should be placed next to your main file. (Example: `src/id/riztranslation/src/eu/kanade/tachiyomi/extension/id/riztranslation/UrlActivity.kt`)

`AndroidManifest.xml` example :

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application>
        <activity
            android:name=".id.riztranslation.UrlActivity"
            android:excludeFromRecents="true"
            android:exported="true"
            android:theme="@android:style/Theme.NoDisplay">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />

                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />

                <data
                    android:host="riztranslation.pages.dev"
                    android:pathPattern="/..*"
                    android:scheme="https" />
                <data
                    android:host="riztranslation.rf.gd"
                    android:pathPattern="/..*"
                    android:scheme="https" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

The `AndroidManifest.xml` file will contain an `android:name` attribute that refers to the path of your `UrlActivity.kt` file. For example, if the extension is Riztranslation, the `android:name` will be `.id.riztranslation.UrlActivity`.

Next, you have the `<data android:scheme="https" android:host="host" android:pathPattern="/..*" />` element; you can have it multiple times, which allows you to specify the URL that can be opened in Mihon. You can read more about this in Android's [`<data>` documentation](https://developer.android.com/guide/topics/manifest/data-element).

Now, as for `UrlActivity`, you can just use the example below.

> [!CAUTION]
> The activity does not support any Kotlin Intrinsics specific methods or calls,
> and using them will cause crashes in the activity. Consider using Java's equivalent
> methods instead, such as using `String`'s `equals()` instead of using `==`.
>
> You can use Kotlin Intrinsics in the extension source class, this limitation only
> applies to the activity classes.

To explain how it works, it will trigger Mihon's `SEARCH` action, passing the URL as a query and specifying that it comes from your extension to narrow down the search. Avoid putting any logic in this file; instead, implement it in your extension's class.

```kotlin
class UrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intentData = intent?.data?.toString()
        if (intentData != null) {
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", intentData)
                putExtra("filter", packageName)
            }
            try {
                startActivity(mainIntent)
            } catch (e: Throwable) {
                Log.e("RiztranslationUrl", e.toString())
            }
        } else {
            Log.e("RiztranslationUrl", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
```

Now all you need to do is adapt the search function (`fetchSearchManga`) in your extension so that, given a URL, it returns a single manga that matches that URL. For example:

```kotlin
if (query.startsWith("https://")) {
    val url = query.toHttpUrlOrNull()
    if (url != null && url.host == baseUrl.toHttpUrl().host) {
        val typeIndex = url.pathSegments.indexOfFirst { it == "detail" || it == "view" }
        if (typeIndex != -1 && typeIndex + 1 < url.pathSize) {
            val id = url.pathSegments[typeIndex + 1]
            return GET("$apiUrl/Book?select=id,judul,cover&type=not.ilike.*novel*&id=eq.$id", apiHeaders)
        }
    }
}
```

To test if the URL intent filter is working as expected, you can try opening the website in a browser
and navigating to the endpoint that was added as a filter or clicking a hyperlink. Alternatively,
you can use the `adb` command below.

```bash
adb shell am start -d "<your-link>" -a android.intent.action.VIEW
```

You can find a complete example of how URLs work in the [Riztranslation extension](https://github.com/keiyoushi/extensions-source/tree/main/src/id/riztranslation).

#### Update strategy

In some cases, titles in a source will always have the same chapter list (i.e., they are immutable).
These do not need to be included in global app updates. Excluding them saves a lot of network requests
and prevents unnecessary load on the source servers. To change the update strategy of a `SManga`,
use the `update_strategy` field. You can find below a description of the current possible values.

- `UpdateStrategy.ALWAYS_UPDATE`: Titles marked as always update will be included in the library
update if they aren't excluded by additional restrictions.
- `UpdateStrategy.ONLY_FETCH_ONCE`: Titles marked as only fetch once will be automatically skipped
during library updates. Useful for cases where the series is previously known to be finished and have
only a single chapter, for example.

If not set, it defaults to `ALWAYS_UPDATE`.

#### Renaming existing sources

There are some cases where existing sources change their names on the website. To correctly reflect
these changes in the extension, you need to explicitly set the `id` to the same old value, otherwise
it will get changed by the new `name` value and users will be forced to migrate back to the source.

To get the current `id` value before the name change, you can search the source name in the [repository JSON file](https://github.com/keiyoushi/extensions/blob/repo/index.json)
by looking at the `sources` attribute of the extension. When you have the `id` copied, you can
override it in the source:

```kotlin
override val id: Long = <the-id>
```

Then the class name and the `name` attribute value can be changed. Also don't forget to update the
extension name and class name in the individual Gradle file.

> [!IMPORTANT]
> The package name **needs** to be the same (even if it has the old name), otherwise users will not
> receive the extension update when it gets published in the repository.

The `id` also needs to be explicitly set to the old value if you're changing the `lang` attribute.

> [!NOTE]
> If the source has also changed their theme you can instead just change
> the `name` field in the source class and in the Gradle file. By doing so
> a new `id` will be generated and users will be forced to migrate.

## Multi-source themes

The `lib-multisrc` directory houses source code that is useful in situations where multiple source
sites use the same site generator tool (usually a CMS) for bootstrapping their website and this makes
them similar enough to prompt code reuse through inheritance/composition; which from now on we will
use the general **theme** term to refer to.

Themes are provided as libraries within `lib-multisrc`. You can apply a theme to an extension by specifying the `themePkg` property in its `build.gradle` file.

### Creating a new theme

To create a new theme, you need to set up a new module inside the `lib-multisrc` directory. The structure is similar to a regular extension, but it acts as a base library that other extensions can depend on.

#### Theme directory structure

```console
$ tree lib-multisrc/<theme_name>/
lib-multisrc/<theme_name>/
├── build.gradle.kts
└── src
    └── main
        └── java
            └── eu
                └── kanade
                    └── tachiyomi
                        └── multisrc
                            └── <theme_name>
                                └── <ThemeName>.kt
```

`<theme_name>` should be adapted from the CMS/theme name, and can only contain lowercase ASCII letters and digits. Your theme code must be placed in the package `eu.kanade.tachiyomi.multisrc.<theme_name>`.

#### Theme build.gradle.kts

Make sure that your new theme's `build.gradle.kts` file follows this structure:

```kotlin
plugins {
    id("lib-multisrc")
}

baseVersionCode = 1
```

| Field             | Description                                                                                                                                                                   |
|-------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `baseVersionCode` | The base version code for the theme. This must be a positive integer and **incremented** whenever a change is made to the theme's implementation that affects the extensions. |

#### Theme main class

The main class of the theme (e.g., `<ThemeName>.kt`) contains the default implementation for the source sites. It should be declared as an `abstract class` extending `HttpSource`, allowing individual extensions to inherit and override its properties and methods.

```kotlin
package eu.kanade.tachiyomi.multisrc.<theme_name>

import eu.kanade.tachiyomi.source.online.HttpSource

abstract class <ThemeName>(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    // Theme default implementation...

}
```

### Using a Theme

To use a theme in your extension, follow the regular extension creation steps and add the `themePkg` property to your `build.gradle`:

```groovy
ext {
    extName = '<My source name>'
    extClass = '.<MySourceName>'
    themePkg = '<theme_name>'
    overrideVersionCode = 1
    isNsfw = true
}

apply from: "$rootDir/common.gradle"
```

Notice that instead of `extVersionCode`, extensions using a theme must use `overrideVersionCode`. The final extension version code (`extVersionCode`) is automatically calculated during the build process as `theme.baseVersionCode + ext.overrideVersionCode`.

Because themes are provided as libraries, your extension's main class will directly inherit from the theme's base class.

Any site-specific overrides, custom functions, or custom icons are implemented directly in your extension's module (`src/<lang>/<mysourcename>`) by overriding the inherited theme properties and functions.

## Running

For local development, use the following run configuration to launch the app directly into the Browse panel.

![Android Studio: Run/Debug Configurations](https://i.imgur.com/6s2dvax.png)

Copy the following into `Launch Flags` for the Debug build of Mihon:

```bash
-W -S -n app.mihon.dev/eu.kanade.tachiyomi.ui.main.MainActivity -a eu.kanade.tachiyomi.SHOW_CATALOGUES
```

For other builds, replace  `app.mihon.dev` with the corresponding package IDs:

- Release build: `app.mihon`
- Preview build: `app.mihon.debug`

If the extension builds and runs successfully, then the code changes should be ready to test in your local app.

> [!IMPORTANT]
> If you're deploying to Android 11 or higher, enable the `Always install with package manager` option in the run configurations. Without this option enabled, you might face issues such as Android Studio running an older version of the extension without the modifications you might have done.

## Debugging

### Android Debugger

> [!NOTE]
> It is generally recommended to rely on logging instead of the Android Debugger. Using standard logs (like `Log.d` or viewing OkHttp logs) is typically much faster, easier to set up, and is more than sufficient for debugging web scraping logic.

> [!IMPORTANT]
> If you didn't **build the main app** from source with **debug enabled** and are using a release/beta APK, you **need a rooted device**.
> If you are using an **emulator** instead, make sure you choose a profile **without Google Play**.

Follow the steps above for building and running locally if you haven't already. Debugging will not work if you did not follow the steps above.

You can leverage the Android Debugger to add breakpoints and step through your extension while debugging.

You *cannot* simply use Android Studio's `Debug 'module.name'` -> this will most likely result in an
error while launching.

Instead, once you've built and installed your extension on the target device, use
`Attach Debugger to Android Process` to start debugging the app.

Inside the `Attach Debugger to Android Process` window, once the app is running on your device and `Show all processes` is checked, you should be able to select `app.mihon.dev` and press OK.

![Android Studio: Choose Process](https://i.imgur.com/SUhdB52.png)

### Logs

You can also elect to simply rely on logs printed from your extension, which
show up in the [`Logcat`](https://developer.android.com/studio/debug/am-logcat) panel of Android Studio.

### Inspecting network calls

One of the easiest ways to inspect network issues (such as HTTP errors 404, 429, no chapter found, etc.)
is to use the [`Logcat`](https://developer.android.com/studio/debug/am-logcat) panel of Android Studio
and filter by the `OkHttpClient` tag.

To be able to check the calls made by OkHttp, you need to enable verbose logging in the app, which is
not enabled by default. To enable it, go to
More -> Settings -> Advanced -> Verbose logging. After enabling it, don't forget to restart the app.

Inspecting the Logcat allows you to get a good look at the call flow and is more than enough in most
cases where issues occur. However, alternatively, you can also use an external tool like `mitm-proxy`.
For that, refer to the subsequent sections.

On newer Android Studio versions, you can use its built-in Network Inspector inside the
App Inspection tool window. This feature provides a nice GUI to inspect the requests made in the app.

To use it, follow the [official documentation](https://developer.android.com/studio/debug/network-profiler)
and select the app's package name in the process list.

### Using external network inspecting tools

If you want a deeper look into the network flow, such as inspecting the request and response bodies
you can use an external tool like `mitm-proxy`.

#### Setup your proxy server

We are going to use [mitm-proxy](https://mitmproxy.org/) but you can replace it with any other Web
Debugger (i.e. Charles, Burp Suite, Fiddler etc). To install and execute, follow the commands below.

```console
# Install the tool.
$ sudo pip3 install mitmproxy
# Execute the web interface and the proxy.
$ mitmweb
```

Alternatively, you can also use the Docker image:

```bash
$ docker run --rm -it -p 8080:8080 \
    -p 127.0.0.1:8081:8081 \
    --web-host 0.0.0.0 \
    mitmproxy/mitmproxy mitmweb
```

After installing and running, open your browser and navigate to <http://127.0.0.1:8081>.

#### OkHttp proxy setup

Since most of the manga sources are going to use HTTPS, we need to disable SSL verification in order
to use the web debugger. For that, add this code to inside your source class:

```kotlin
package eu.kanade.tachiyomi.extension.en.mysource

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class MySource : HttpSource() {
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

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .ignoreAllSSLErrors()
        .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("10.0.2.2", 8080)))
        .build()
}
```

Note: `10.0.2.2` is usually the address of your loopback interface in the android emulator. If
the app tells you that it's unable to connect to 10.0.2.2:8080 you will likely need to change it
(the same if you are using hardware device).

If all went well, you should see all requests and responses made by the source in the web interface
of `mitmweb`.

## Building

APKs can be created in Android Studio via `Build > Build Bundle(s) / APK(s) > Build APK(s)` or
`Build > Generate Signed Bundle / APK`.

If for some reason you decide to build the APK from the command line, you can use the following
command (because you're doing things differently than expected, I assume you have some
knowledge of gradlew and your OS):

```console
// For a single apk, use this command
$ ./gradlew src:<lang>:<source>:assembleDebug
```

## Submitting the changes

When you feel confident about your changes, submit a new Pull Request so your code can be reviewed
and merged if it's approved. We encourage following a [GitHub Standard Fork & Pull Request Workflow](https://gist.github.com/Chaser324/ce0505fbed06b947d962)
and following the good practices of the workflow, such as not committing directly to `main`: always
create a new branch for your changes.

If you are more comfortable about using Git GUI-based tools, you can refer to [this guide](https://learntodroid.com/how-to-use-git-and-github-in-android-studio/)
about the Git integration inside Android Studio, specifically the "How to Contribute to an to Existing
Git Repository in Android Studio" section of the guide.

> [!IMPORTANT]
> Make sure you have generated the extension icon using the linked Icon Generator tool in the [Tools](#tools)
> section. The icon **must follow the pattern** adopted by all other extensions: a square with rounded
> corners. Make sure to remove the generated `web_hi_res_512.png`.

Please **do test your changes by compiling it through Android Studio** before submitting it. Obvious
untested PRs will not be merged, such as ones created with the GitHub web interface. Also make sure
to follow the PR checklist available in the PR body field when creating a new PR. As a reference, you
can find it below.

### Pull Request checklist

- Updated `extVersionCode` value in `build.gradle` for individual extensions
- Updated `overrideVersionCode` or `baseVersionCode` as needed for all multisrc extensions
- Referenced all related issues in the PR body (e.g. "Closes #xyz")
- Added the `isNsfw = true` flag in `build.gradle` when appropriate
- Have not changed source names
- Have explicitly kept the `id` if a source's name or language were changed
- Have tested the modifications by compiling and running the extension through Android Studio
- Have removed `web_hi_res_512.png` when adding a new extension
