import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Arab Hentai"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "هنتاي العرب - نت"
        lang = "ar"
        baseUrl = "https://arabhentai.net"
    }
}
