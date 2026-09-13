import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Shinigami"
    versionCode = 82
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    deeplink {
        host("shinigami.asia")
        host("*.shinigami.asia")
        path("/series/..*")
    }

    source {
        lang = "id"
        baseUrl = "https://11.shinigami.asia"
        id = 3411809758861089969L
    }
}
