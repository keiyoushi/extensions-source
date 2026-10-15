import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Arabs Hentai"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "هنتاي العرب"
        lang = "ar"
        baseUrl = "https://arabshentai.com"
    }
}
