import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga18fx"
    versionCode = 58
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("all", "en").forEach { language ->
        source {
            lang = language
            baseUrl = "https://manga18fx.com"

            if (language == "en") id = 3157287889751723714L
        }
    }
}
