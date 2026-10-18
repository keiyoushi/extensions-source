import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Xipi Manhua"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "嬉皮漫畫"
        lang = "zh"
        baseUrl = "https://m.hipmh.com"
    }

    deeplink {
        path("/works/..*")
        path("/..*/works/..*")
        path("/chapter/go")
        path("/..*/chapter/go")
    }

    deeplink {
        host("reader.hipmh.top")
        path("/chapter/..*")
        path("/..*/chapter/..*")
    }
}
