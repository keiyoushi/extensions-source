import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pornhwa.fr"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        name = "Pornwha.fr"
        lang = "fr"
        baseUrl = "https://pornhwa.fr"
    }
}
