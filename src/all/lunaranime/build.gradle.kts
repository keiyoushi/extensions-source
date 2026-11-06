import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lunar Manga"
    versionCode = 7
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    val languages = listOf(
        "all",
        "en",
        "id",
        "ja",
    )

    deeplink {
        host("lunarx.to")
        path("/manga/..*")
    }

    languages.forEach { language ->
        source {
            baseUrl = "https://lunarx.to"
            lang = language
        }
    }
}
