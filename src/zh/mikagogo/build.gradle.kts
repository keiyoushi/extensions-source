import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mikagogo"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "米卡插画"
        lang = "zh"
        baseUrl = "https://mikagogo.com"
    }

    deeplink {
        host("mikagogo.com")
        host("www.mikagogo.com")
        path("/..*")
    }
}
