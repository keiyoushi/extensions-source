import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HDoujin"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    listOf("all", "en", "es", "ja", "ko", "zh").forEach {
        source {
            lang = it
            baseUrl = "https://hdoujin.org"
            if (it == "ko") id = 8377507648400729012L
        }
    }
}
