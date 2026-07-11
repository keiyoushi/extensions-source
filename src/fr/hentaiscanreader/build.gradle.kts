import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai Scan Reader"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "scanreader"

    source {
        lang = "fr"
        baseUrl = "https://hentai.scanreader.net"
    }
}
