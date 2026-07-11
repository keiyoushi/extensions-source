import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sodsaime"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        name = "สดใสเมะ"
        lang = "th"
        baseUrl = "https://www.xn--l3c0azab5a2gta.com"
    }
}
