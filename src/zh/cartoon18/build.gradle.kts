import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Cartoon18"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "zh"
        baseUrl = "https://www.cartoon18.com"
    }

    deeplink {
        host("www.cartoon18.com")
        host("cartoon18.com")
        path("/v/..*")
        path("/zh-hans/v/..*")
    }
}
