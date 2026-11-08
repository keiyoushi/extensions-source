import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ACGN"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://comic.acgn.cc"
        lang = "zh"
    }

    deeplink {
        path("/manhua-..*")
        path("/view-..*")
    }
}
