import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaHome"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://www.mangahome.com"
    }

    deeplink {
        host("mangahome.com")
        host("www.mangahome.com")
        host("m.mangahome.com")
        path("/manga/..*")
    }
}

dependencies {
    implementation(project(":lib:unpacker"))
}
