import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Taiyō"
    versionCode = 11
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://taiyo.moe"
    }

    deeplink {
        host("taiyo.moe")
        path("/media/..*")
    }
}
