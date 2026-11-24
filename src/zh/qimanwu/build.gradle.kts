import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Qimanwu"
    theme = "mccms"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "奇漫屋"
        baseUrl = "https://www.qimanwu.app"
        lang = "zh"
    }

    deeplink {
        host("www.qimanwu.app")
        host("m.qimanwu.app")
        host("qimanwu.app")
        path("/comic/..*")
        path("/chapter/..*")
    }
}
