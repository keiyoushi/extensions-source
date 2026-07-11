import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa18.net"
    versionCode = 13
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "Manhwa18.Net"
        lang = "en"
        baseUrl = "https://manhwa18.net"
        versionId = 2
    }
}
