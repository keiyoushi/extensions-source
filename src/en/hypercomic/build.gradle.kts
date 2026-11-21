import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HYPERCOMIC"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://play.hypercomic.io"
    }

    deeplink {
        path("/Webtoon/episode/..*")
        path("/Webtoon/episodeview/..*")
    }
}
