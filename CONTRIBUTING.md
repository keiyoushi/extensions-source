# Contributing

This guide have some instructions and tips on how to create a new Tachiyomi extension. Please **read
it carefully** if you're a new contributor or don't have any experience on the required languages 
and knowledges.

This guide is not definitive and it's being updated over time. If you find any issue on it, feel 
free to report it through a [Meta Issue](https://github.com/keiyoushi/extensions-source/issues/new?assignees=&labels=Meta+request&template=06_request_meta.yml) 
or fixing it directly by submitting a Pull Request.

## Table of Contents

1. [Prerequisites](#prerequisites)
   1. [Tools](#tools)
   2. [Cloning the repository](#cloning-the-repository)
2. [Getting help](#getting-help)
3. [Writing an extension](#writing-an-extension)
   1. [Setting up a new Gradle module](#setting-up-a-new-gradle-module)
   2. [Core dependencies](#core-dependencies)
   3. [Extension main class](#extension-main-class)
   4. [Extension call flow](#extension-call-flow)
   5. [Misc notes](#misc-notes)
   6. [Advanced extension features](#advanced-extension-features)
4. [Multi-source themes](#multi-source-themes)
   1. [The directory structure](#the-directory-structure)
   2. [Development workflow](#development-workflow)
   3. [Scaffolding overrides](#scaffolding-overrides)
   4. [Additional Notes](#additional-notes)
5. [Running](#running)
6. [Debugging](#debugging)
   1. [Android Debugger](#android-debugger)
   2. [Logs](#logs)
   3. [Inspecting network calls](#inspecting-network-calls)
   4. [Using external network inspecting tools](#using-external-network-inspecting-tools)
7. [Building](#building)
8. [Submitting the changes](#submitting-the-changes)
   1. [Pull Request checklist](#pull-request-checklist)

## Prerequisites

Before you start, please note that the ability to use following technologies is **required** and 
that existing contributors will not actively teach them to you.

- Basic [Android development](https://developer.android.com/)
- [Kotlin](https://kotlinlang.org/)
- Web scraping
    - [HTML](https://developer.mozilla.org/en-US/docs/Web/HTML)
    - [CSS selectors](https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_Selectors)
    - [OkHttp](https://square.github.io/okhttp/)
    - [JSoup](https://jsoup.org/)

### Tools

- [Android Studio](https://developer.android.com/studio)
- Emulator or phone with developer options enabled and a recent version of Tachiyomi installed
- [Icon Generator](https://as280093.github.io/AndroidAssetStudio/icons-launcher.html)

### Cloning the repository

Some alternative steps can be followed to skip unrelated sources, which will make it faster to pull,
navigate and build. This will also reduce disk usage and network traffic.

**These steps are only needed when the repo is huge and contains a lot of sources. If the repo is
small, just do a normal full clone instead.**

<details><summary>Steps</summary>

1. Do a partial clone.
    ```bash
    git clone --filter=blob:none --sparse <fork-repo-url>
    cd extensions/
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
    git sparse-checkout add .run buildSrc core gradle lib multisrc/src/main/java/generator
    # add a single source
    git sparse-checkout add src/<lang>/<source>
    # add a multisrc theme
    git sparse-checkout add multisrc/src/main/java/eu/kanade/tachiyomi/multisrc/<source>
    git sparse-checkout add multisrc/overrides/<source>
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
    !/multisrc/overrides/*
    !/multisrc/src/main/java/eu/kanade/tachiyomi/multisrc/*
    # allow a single source
    /src/<lang>/<source>
    # allow a multisrc theme
    /multisrc/src/main/java/eu/kanade/tachiyomi/multisrc/<source>
    /multisrc/overrides/<source>
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
    # on main branch, which is not a good practice.
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
developing your extension. When doing so, please ask it in the `#programming` channel.
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

By default, all individual and generated multisrc extensions are loaded for local development.
This may be inconvenient if you only need to work on one extension at a time.

To adjust which modules are loaded, make adjustments to the `settings.gradle.kts` file as needed.

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
                            └── <MySourceName>.kt

13 directories, 9 files
```

`<lang>` should be an ISO 639-1 compliant language code (two letters or `all`). `<mysourcename>`
should be adapted from the site name, and can only contain lowercase ASCII letters and digits.
Your extension code must be placed in the package `eu.kanade.tachiyomi.extension.<lang>.<mysourcename>`.

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
some interfaces and stubs from the [app](https://github.com/tachiyomiorg/tachiyomi) for compilation
purposes. The actual implementations can be found [here](https://github.com/tachiyomiorg/tachiyomi/tree/master/app/src/main/java/eu/kanade/tachiyomi/source).
Referencing the actual implementation will help with understanding extensions' call flow.

#### DataImage library

[`lib-dataimage`](https://github.com/keiyoushi/extensions-source/tree/main/lib/dataimage) is a library 
for handling [base 64 encoded image data](https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/Data_URIs)
using an [OkHttp interceptor](https://square.github.io/okhttp/interceptors/).

```groovy
dependencies {
    implementation(project(':lib-dataimage'))
}
```

#### i18n library

[`lib-i18n`](https://github.com/keiyoushi/extensions-source/tree/main/lib/i18n) is a library for handling
internationalization in the sources. It allows loading `.properties` files with messages located under
the `assets/i18n` folder of each extension, that can be used to translate strings under the source.

```groovy
dependencies {
    implementation(project(':lib-i18n'))
}
```

#### Additional dependencies

If you find yourself needing additional functionality, you can add more dependencies to your `build.gradle` 
file. Many of [the dependencies](https://github.com/tachiyomiorg/tachiyomi/blob/master/app/build.gradle.kts) 
from the main Tachiyomi app are exposed to extensions by default.

> [!NOTE]
> Several dependencies are already exposed to all extensions via Gradle's version catalog.
> To view which are available check the `gradle/libs.versions.toml` file.

Notice that we're using `compileOnly` instead of `implementation` if the app already contains it. 
You could use `implementation` instead for a new dependency, or you prefer not to rely on whatever 
the main app has at the expense of app size.

> [!IMPORTANT]
> Using `compileOnly` restricts you to versions that must be compatible with those used in 
> [the latest stable version of Tachiyomi](https://github.com/tachiyomiorg/tachiyomi/releases/latest).

### Extension main class

The class which is referenced and defined by `extClass` in `build.gradle`. This class should implement
either `SourceFactory` or extend one of the `Source` implementations: `HttpSource` or `ParsedHttpSource`.

| Class              | Description                                                                                                                      |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------|
| `SourceFactory`    | Used to expose multiple `Source`s. Use this in case of a source that supports multiple languages or mirrors of the same website. |
| `HttpSource`       | For online source, where requests are made using HTTP.                                                                           |
| `ParsedHttpSource` | Similar to `HttpSource`, but has methods useful for scraping pages.                                                              |

#### Main class key variables

| Field     | Description                                                                                                                                                     |
|-----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `name`    | Name displayed in the "Sources" tab in Tachiyomi.                                                                                                               |
| `baseUrl` | Base URL of the source without any trailing slashes.                                                                                                            |
| `lang`    | An ISO 639-1 compliant language code (two letters in lower case in most cases, but can also include the country/dialect part by using a simple dash character). |
| `id`      | Identifier of your source, automatically set in `HttpSource`. It should only be manually overriden if you need to copy an existing autogenerated ID.            |

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
    - You should set `thumbnail_url` if is available, if not, `getMangaDetails` will be **immediately**
    called (this will increase network calls heavily and should be avoided).

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

The search flow have support to filters that can be added to a `FilterList` inside the `getFilterList`
method. When the user changes the filters' state, they will be passed to the `searchRequest`, and they
can be iterated to create the request (by getting the `filter.state` value, where the type varies
depending on the `Filter` used). You can check the filter types available [here](https://github.com/tachiyomiorg/tachiyomi/blob/master/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/model/Filter.kt)
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

All control filters can have a default state set. It's usually recommended if the source have filters
to make the initial state match the popular manga list, so when the user open the filter sheet, the
state is equal and represents the current manga showing.

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
    - `SManga.status` is an "enum" value. Refer to [the values in the `SManga` companion object](https://github.com/tachiyomiorg/extensions-lib/blob/master/library/src/main/java/eu/kanade/tachiyomi/source/model/SManga.kt#L26).
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

- After a chapter list for the manga is fetched and the app is going to cache the data,
`prepareNewChapter` will be called.
- `SChapter.date_upload` is the [UNIX Epoch time](https://en.wikipedia.org/wiki/Unix_time) 
**expressed in milliseconds**.
    - If you don't pass `SChapter.date_upload` and leave it zero, the app will use the default date
    instead, but it's recommended to always fill it if it's available.
    - To get the time in milliseconds from a date string, you can use a `SimpleDateFormat` like in 
    the example below.

      ```kotlin
      private fun parseDate(dateStr: String): Long {
          return runCatching { DATE_FORMATTER.parse(dateStr)?.time }
              .getOrNull() ?: 0L
      }

      companion object {
          private val DATE_FORMATTER by lazy {
              SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
          }
      }
      ```

      Make sure you make the `SimpleDateFormat` a class constant or variable so it doesn't get 
    recreated for every chapter. If you need to parse or format dates in manga description, create 
    another instance since `SimpleDateFormat` is not thread-safe.
    - If the parsing have any problem, make sure to return `0L` so the app will use the default date
    instead.
    - The app will overwrite dates of existing old chapters **UNLESS** `0L` is returned.
    - The default date has [changed](https://github.com/tachiyomiorg/tachiyomi/pull/7197) in 
    preview ≥ r4442 or stable > 0.13.4.
      - In older versions, the default date is always the fetch date.
      - In newer versions, this is the same if every (new) chapter has `0L` returned.
      - However, if the source only provides the upload date of the latest chapter, you can now set
      it to the latest chapter and leave other chapters default. The app will automatically set it (instead of fetch date) to every new chapter and leave old chapters' dates untouched.
- `getChapterUrl` is called when the user taps "Open in WebView" in the reader.
  - If the source uses an API to fetch the data, consider overriding this method to return the
  chapter absolute URL in the website instead.
  - It defaults to the URL provided to the request in `pageListRequest`.

#### Chapter Pages

- When user opens a chapter, `getPageList` will be called and it will return a list of `Page`s.
- While a chapter is open in the reader or is being downloaded, `fetchImageUrl` will be called to get
URLs for each page of the manga if the `Page.imageUrl` is empty.
- If the source provides all the `Page.imageUrl`'s directly, you can fill them and let the `Page.url`
empty, so the app will skip the `fetchImageUrl` source and call directly `fetchImage`.
- The `Page.url` and `Page.imageUrl` attributes **should be set as an absolute URL**.
- Chapter pages numbers start from `0`.
- The list of `Page`s should be returned already sorted, the `index` field is ignored.

### Misc notes

- Sometimes you may find no use for some inherited methods. If so just override them and throw 
exceptions: `throw UnsupportedOperationException()`
- You probably will find `getUrlWithoutDomain` useful when parsing the target source URLs. Keep in 
mind there's a current issue with spaces in the URL though, so if you use it, replace all spaces with
URL encoded characters (like `%20`).
- If possible try to stick to the general workflow from `HttpSource`/`ParsedHttpSource`; breaking 
them may cause you more headache than necessary.
- By implementing `ConfigurableSource` you can add settings to your source, which is backed by
[`SharedPreferences`](https://developer.android.com/reference/android/content/SharedPreferences).

### Advanced Extension features

#### URL intent filter

Extensions can define URL intent filters by defining it inside a custom `AndroidManifest.xml` file.
(Example TBD.)

To test if the URL intent filter is working as expected, you can try opening the website in a browser 
and navigating to the endpoint that was added as a filter or clicking a hyperlink. Alternatively, 
you can use the `adb` command below.

```console
$ adb shell am start -d "<your-link>" -a android.intent.action.VIEW
```

> [!CAUTION]
> The activity does not support any Kotlin Intrinsics specific methods or calls,
> and using them will causes crashes in the activity. Consider using Java's equivalent
> methods instead, such as using `String`'s `equals()` instead of using `==`.
>
> You can use Kotlin Intrinsics in the extension source class, this limitation only
> applies to the activity classes.

#### Update strategy

There is some cases where titles in a source will always only have the same chapter list
(i.e. immutable), and don't need to be included in a global update of the app because of that, saving
a lot of requests and preventing causing unnecessary damage to the source servers. To change the
update strategy of a `SManga`, use the `update_strategy` field. You can find below a description of
the current possible values.

- `UpdateStrategy.ALWAYS_UPDATE`: Titles marked as always update will be included in the library 
update if they aren't excluded by additional restrictions.
- `UpdateStrategy.ONLY_FETCH_ONCE`: Titles marked as only fetch once will be automatically skipped
during library updates. Useful for cases where the series is previously known to be finished and have
only a single chapter, for example.

If not set, it defaults to `ALWAYS_UPDATE`.

#### Renaming existing sources

There is some cases where existing sources changes their name on the website. To correctly reflect
these changes in the extension, you need to explicity set the `id` to the same old value, otherwise
it will get changed by the new `name` value and users will be forced to migrate back to the source.

To get the current `id` value before the name change, you can search the source name in the [repository JSON file](https://github.com/keiyoushi/extensions/blob/repo/index.json)
by looking into the `sources` attribute of the extension. When you have the `id` copied, you can
override it in the source:

```kotlin
override val id: Long = <the-id>
```

Then the class name and the `name` attribute value can be changed. Also don't forget to update the
extension name and class name in the individual Gradle file.

> [!IMPORTANT]
> The package name **needs** to be the same (even if it has the old name), otherwise users will not 
> receive the extension update when it gets published in the repository.

The `id` also needs to be explicity set to the old value if you're changing the `lang` attribute.

> [!NOTE]
> If the source has also changed their theme you can instead just change
> the `name` field in the source class and in the Gradle file. By doing so
> a new `id` will be generated and users will be forced to migrate.

## Multi-source themes
The `multisrc` module houses source code for generating extensions for cases where multiple source
sites use the same site generator tool (usually a CMS) for bootsraping their website and this makes
them similar enough to prompt code reuse through inheritance/composition; which from now on we will
use the general **theme** term to refer to.

This module contains the *default implementation* for each theme and definitions for each source that
builds upon that default implementation and also it's overrides upon that default implementation,
all of this becomes a set of source code which then is used to generate individual extensions from.

### The directory structure
```console
$ tree multisrc
multisrc
├── build.gradle.kts
├── overrides
│   └── <themepkg>
│       ├── default
│       │   ├── additional.gradle
│       │   └── res
│       │       ├── mipmap-hdpi
│       │       │   └── ic_launcher.png
│       │       ├── mipmap-mdpi
│       │       │   └── ic_launcher.png
│       │       ├── mipmap-xhdpi
│       │       │   └── ic_launcher.png
│       │       ├── mipmap-xxhdpi
│       │       │   └── ic_launcher.png
│       │       └── mipmap-xxxhdpi
│       │           └── ic_launcher.png
│       └── <sourcepkg>
│           ├── additional.gradle
│           ├── AndroidManifest.xml
│           ├── res
│           │   ├── mipmap-hdpi
│           │   │   └── ic_launcher.png
│           │   ├── mipmap-mdpi
│           │   │   └── ic_launcher.png
│           │   ├── mipmap-xhdpi
│           │   │   └── ic_launcher.png
│           │   ├── mipmap-xxhdpi
│           │   │   └── ic_launcher.png
│           │   └── mipmap-xxxhdpi
│           │       └── ic_launcher.png
│           └── src
│               └── <SourceName>.kt
└── src
    └── main
        ├── AndroidManifest.xml
        └── java
            ├── eu
            │   └── kanade
            │       └── tachiyomi
            │           └── multisrc
            │               └── <themepkg>
            │                   ├── <ThemeName>Generator.kt
            │                   └── <ThemeName>.kt
            └── generator
                ├── GeneratorMain.kt
                ├── IntelijConfigurationGeneratorMain.kt
                └── ThemeSourceGenerator.kt
```

- `multisrc/src/main/java/eu/kanade/tachiyomi/multisrc/<themepkg>/<Theme>.kt` defines the the theme's
default implementation.
- `multisrc/src/main/java/eu/kanade/tachiyomi/multisrc/<theme>/<Theme>Generator.kt` defines the the
theme's generator class, this is similar to a `SourceFactory` class.
- `multisrc/overrides/<themepkg>/default/res` is the theme's default icons, if a source doesn't have
overrides for `res`, then default icons will be used.
- `multisrc/overrides/<themepkg>/default/additional.gradle` defines additional gradle code, this will
be copied at the end of all generated sources from this theme.
- `multisrc/overrides/<themepkg>/<sourcepkg>` contains overrides for a source that is defined inside
the `<Theme>Generator.kt` class.
- `multisrc/overrides/<themepkg>/<sourcepkg>/src` contains source overrides.
- `multisrc/overrides/<themepkg>/<sourcepkg>/res` contains override for icons.
- `multisrc/overrides/<themepkg>/<sourcepkg>/additional.gradle` defines additional gradle code, this
will be copied at the end of the generated gradle file below the theme's `additional.gradle`.
- `multisrc/overrides/<themepkg>/<sourcepkg>/AndroidManifest.xml` is copied as an override to the
default `AndroidManifest.xml` generation if it exists.

> [!NOTE]
> Files ending with `Gen.kt` (i.e. `multisrc/src/main/java/eu/kanade/tachiyomi/multisrc/<theme>/XxxGen.kt`)
> are considered helper files and won't be copied to generated sources.

### Development workflow
There are three steps in running and testing a theme source:

1. Generate the sources
    - **Option 1: Only generate sources from one theme**
        - **Method 1:** Find and run `<ThemeName>Generator` run configuration from the
        `Run/Debug Configuration` menu.
        - **Method 2:** Directly run `<themepkg>.<ThemeName>Generator.main` by pressing the play
        button in front of the method shown inside Android Studio's Code Editor to generate sources
        from the said theme.
    - **Option 2: Generate sources from all themes**
        - **Method 1:** Run `./gradlew multisrc:generateExtensions` from a terminal window to
        generate all sources.
        - **Method 2:** Directly run `Generator.GeneratorMain.main` by pressing the play button
        in front of the method shown inside Android Studio's Code Editor to generate all sources.
2. Sync gradle to import the new generated sources inside `generated-src`
    - **Method 1:** Android Studio might prompt to sync the gradle. Click on `Sync Now`.
    - **Method 2:** Manually re-sync by opening `File` -> `Sync Project with Gradle Files` or by
    pressing `Alt+f` then `g`.
3. Build and test the generated Extention like normal `src` sources.
    - It's recommended to make changes here to skip going through step 1 and 2 multiple times, and
    when you are done, copying the changes back to `multisrc`.

### Scaffolding overrides
You can use this python script to generate scaffolds for source overrides.
Put it inside `multisrc/overrides/<themepkg>/` as `scaffold.py`.
```python
import os, sys
from pathlib import Path

theme = Path(os.getcwd()).parts[-1]

print(f"Detected theme: {theme}")

if len(sys.argv) < 3:
    print("Must be called with a class name and lang, for Example 'python scaffold.py LeviatanScans en'")
    exit(-1)

source = sys.argv[1]
package = source.lower()
lang = sys.argv[2]

print(f"working on {source} with lang {lang}")

os.makedirs(f"{package}/src")
os.makedirs(f"{package}/res")

with open(f"{package}/src/{source}.kt", "w") as f:
    f.write(f"package eu.kanade.tachiyomi.extension.{lang}.{package}\n\n")
```

### Additional Notes
- Generated sources extension version code is calculated as
`baseVersionCode + overrideVersionCode + multisrcLibraryVersion`.
    - Currently `multisrcLibraryVersion` is `0`
    - When a new source is added, it doesn't need to set `overrideVersionCode` as it's default is `0`.
    - For each time a source changes in a way that should the version increase, `overrideVersionCode`
    should be increased by one.
    - When a theme's default implementation changes, `baseVersionCode` should be increased, the
    initial value should be `1`.
    - For example, for a new theme with a new source, extention version code will be `0 + 0 + 1 = 1`.
- `IntelijConfigurationGeneratorMainKt` should be run on creating or removing a multisrc theme.
    - On removing a theme, you can manually remove the corresponding configuration in the `.run`
    folder instead.
    - Be careful if you're using sparse checkout. If other configurations are accidentally removed,
    `git add` the file you want and `git restore` the others. Another choice is to allow
    `/multisrc/src/main/java/eu/kanade/tachiyomi/multisrc/*` before running the generator.

## Running

To make local development more convenient, you can use the following run configuration to launch 
Tachiyomi directly at the Browse panel:

![](https://i.imgur.com/STy0UFY.png)

If you're running a Preview or debug build of Tachiyomi:

```
-W -S -n eu.kanade.tachiyomi.debug/eu.kanade.tachiyomi.ui.main.MainActivity -a eu.kanade.tachiyomi.SHOW_CATALOGUES
```

And for a release build of Tachiyomi:

```
-W -S -n eu.kanade.tachiyomi/eu.kanade.tachiyomi.ui.main.MainActivity -a eu.kanade.tachiyomi.SHOW_CATALOGUES
```

> [!IMPORTANT]
> If you're deploying to Android 11 or higher, enable the "Always install with package manager" option in the run configurations. Without this option enabled, you might face issues such as Android Studio running an older version of the extension without the modifications you might have done.

## Debugging

### Android Debugger

You can leverage the Android Debugger to step through your extension while debugging.

You *cannot* simply use Android Studio's `Debug 'module.name'` -> this will most likely result in an
error while launching.

Instead, once you've built and installed your extension on the target device, use 
`Attach Debugger to Android Process` to start debugging Tachiyomi.

![](https://i.imgur.com/muhXyfu.png)


### Logs

You can also elect to simply rely on logs printed from your extension, which
show up in the [`Logcat`](https://developer.android.com/studio/debug/am-logcat) panel of Android Studio.

### Inspecting network calls

One of the easiest way to inspect network issues (such as HTTP errors 404, 429, no chapter found etc.)
is to use the [`Logcat`](https://developer.android.com/studio/debug/am-logcat) panel of Android Studio
and filtering by the `OkHttpClient` tag.

To be able to check the calls done by OkHttp, you need to enable verbose logging in the app, that is
not enabled by default and is only included in the Preview versions of Tachiyomi. To enable it, go to
More -> Settings -> Advanced -> Verbose logging. After enabling it, don't forget to restart the app.

Inspecting the Logcat allows you to get a good look at the call flow and it's more than enough in most
cases where issues occurs. However, alternatively, you can also use an external tool like `mitm-proxy`.
For that, refer to the subsequent sections.

On newer Android Studio versions, you can use its built-in Network Inspector inside the
App Inspection tool window. This feature provides a nice GUI to inspect the requests made in the app.

To use it, follow the [official documentation](https://developer.android.com/studio/debug/network-profiler)
and select Tachiyomi package name in the process list.

### Using external network inspecting tools
If you want to take a deeper look into the network flow, such as taking a look into the request and
response bodies, you can use an external tool like `mitm-proxy`.

#### Setup your proxy server
We are going to use [mitm-proxy](https://mitmproxy.org/) but you can replace it with any other Web
Debugger (i.e. Charles, Burp Suite, Fiddler etc). To install and execute, follow the commands below.

```console
Install the tool.
$ sudo pip3 install mitmproxy
Execute the web interface and the proxy.
$ mitmweb
```

Alternatively, you can also use the Docker image:

```
$ docker run --rm -it -p 8080:8080 \
    -p 127.0.0.1:8081:8081 \
    --web-host 0.0.0.0 \
    mitmproxy/mitmproxy mitmweb
```

After installing and running, open your browser and navigate to http://127.0.0.1:8081.

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
Tachiyomi tells you that it's unable to connect to 10.0.2.2:8080 you will likely need to change it
(the same if you are using hardware device).

If all went well, you should see all requests and responses made by the source in the web interface
of `mitmweb`.

## Building

APKs can be created in Android Studio via `Build > Build Bundle(s) / APK(s) > Build APK(s)` or 
`Build > Generate Signed Bundle / APK`.

## Submitting the changes

When you feel confident about your changes, submit a new Pull Request so your code can be reviewed
and merged if it's approved. We encourage following a [GitHub Standard Fork & Pull Request Workflow](https://gist.github.com/Chaser324/ce0505fbed06b947d962)
and following the good practices of the workflow, such as not commiting directly to `main`: always
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
