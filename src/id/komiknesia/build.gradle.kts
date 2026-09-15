import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KomikNesia"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://v1.komiknesiaku.com"
    }

    deeplink {
        host("komiknesiaku.com")
        host("v1.komiknesiaku.com")
        path("/komik/..*")
    }
}
