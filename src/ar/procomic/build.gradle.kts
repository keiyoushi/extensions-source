import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ProComic"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl = "https://procomic.pro"
        lang = "ar"
        versionId = 2
    }

    deeplink {
        host("procomic.pro")
        host("procomic.net")
        path("/..*-..*")
    }
}

dependencies {
    implementation(project(":lib:imagedecoder"))
}
