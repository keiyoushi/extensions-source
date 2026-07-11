import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ComicHubFree"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://comichubfree.com"
    }
}
