import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lura Toon"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "pt-BR"
        baseUrl = "https://luratoons.net"
        versionId = 2
    }

    deeplink {
        path("/..*")
    }
}

dependencies {
    implementation(project(":lib:zipinterceptor"))
}
