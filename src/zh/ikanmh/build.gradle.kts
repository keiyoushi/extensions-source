import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ikanmh"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "漫小肆韓漫"
        lang = "zh"
        baseUrl = "https://ikanmh.xyz"
    }

    deeplink {
        path("/book/..*")
    }
}
