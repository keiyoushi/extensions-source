import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lura Toon"
    versionCode = 59
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://luratoons.net"
        versionId = 2
    }
}

dependencies {

    implementation(project(":lib:randomua"))
    implementation(project(":lib:zipinterceptor"))
}
