import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "177Manhua"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "177漫畫"
        lang = "zh"
        baseUrl = "https://www.177pica.com"
    }

    deeplink {
        host("www.177pica.com")
        host("177pica.com")
        path("/html/..*")
    }
}
