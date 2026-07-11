import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Osaka Scan"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "pt-BR"
        baseUrl = "https://www.osakascan.com"
    }
}
