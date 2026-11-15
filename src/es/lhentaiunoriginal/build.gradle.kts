import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "lHentai (unoriginal)"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://lhentai.com"
    }

    deeplink {
        path("/g/..*")
        path("/tag/..*")
        path("/artist/..*")
    }
}
