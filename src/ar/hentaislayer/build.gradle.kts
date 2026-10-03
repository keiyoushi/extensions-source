import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai Slayer"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "fuzzydoodle"

    source {
        name = "هنتاي سلاير"
        lang = "ar"
        baseUrl = "https://hentaislayer.net"
    }
}
