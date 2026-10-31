import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "WebtoonLib"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl = "https://webtoonlib.to"
        lang = "en"
    }

    deeplink {
        path("/series/..*")
    }
}

dependencies {
    implementation(project(":lib:imagedecoder"))
}
