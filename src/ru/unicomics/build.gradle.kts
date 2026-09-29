import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "UniComics"
    versionCode = 10
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "ru"
        baseUrl = "https://unicomics.ru"
    }

    deeplink {
        host("unicomics.ru")
        path("/comics/series/..*")
    }
}
