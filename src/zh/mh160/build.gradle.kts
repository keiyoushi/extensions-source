import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhua160"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "漫画160"
        lang = "zh-Hans"
        baseUrl = "https://www.mh160mh.com"
    }

    deeplink {
        path("/kanmanhua/..*")
    }
}
