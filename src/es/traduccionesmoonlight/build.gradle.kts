import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Traducciones Moonlight"
    versionCode = 46
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "moonlighttl"

    source {
        lang = "es"
        baseUrl = "https://traduccionesmoonlight.com"
        versionId = 3
    }
}
