import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yaoi Manga Online"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("all", "en", "ja", "zh", "ko", "es").forEach { language ->
        source {
            lang = language
            baseUrl = "https://yaoimangaonline.com"
        }
    }
}
