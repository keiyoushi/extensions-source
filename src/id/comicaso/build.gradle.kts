import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comicaso"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://v3.comicaso.pro"
    }

    deeplink {
        host("v3.comicaso.pro")
        path("/..*")
    }
}

dependencies {
    implementation(project(":lib:randomua"))
}
