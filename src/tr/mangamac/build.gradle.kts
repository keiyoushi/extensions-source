import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaMAC"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "tr"
        baseUrl = "https://mangamac.com"
    }

    deeplink {
        path("/manga/..*")
        path("/webtoon/..*")
        path("/oku/..*")
        path("/oku-webtoon/..*")
    }
}
