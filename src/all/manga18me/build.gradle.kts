import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga18Me"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("all", "en").forEach {
        source {
            name = "Manga18.me"
            lang = it
            baseUrl = "https://manga18.me"
        }
    }
}
