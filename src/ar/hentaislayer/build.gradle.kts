import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai Slayer"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "fuzzydoodle"

    source {
        name = "هنتاي سلاير"
        lang = "ar"
        baseUrl = "https://hentaislayer.net"
    }
}
