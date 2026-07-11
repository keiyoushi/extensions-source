import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaworldAdult"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangaworld"

    source {
        lang = "it"
        baseUrl = "https://www.mangaworldadult.net"
    }
}
