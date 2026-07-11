import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangakawaii"
    versionCode = 39
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "fr"
        baseUrl = "https://www.mangakawaii.io"
    }
}
