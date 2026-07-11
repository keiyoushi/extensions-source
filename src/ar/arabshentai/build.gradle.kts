import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Arabs Hentai"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "هنتاي العرب"
        lang = "ar"
        baseUrl = "https://arabshentai.com"
    }
}
