import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga-TR"
    versionCode = 23
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "tr"
        baseUrl = "https://manga-tr.com"
    }
}
