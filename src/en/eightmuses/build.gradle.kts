import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "8Muses"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "eromuse"

    source {
        lang = "en"
        baseUrl = "https://comics.8muses.com"
    }
}
