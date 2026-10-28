import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Rocks Manga"
    versionCode = 60
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "ar"
        baseUrl = "https://rocksmanga.com"
    }
}
