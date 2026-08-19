import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yomu Comics"
    versionCode = 53
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "pt-BR"
        baseUrl = "https://yomu.com.br"
        id = 1497838059713668619L
    }
}

dependencies {
    implementation(project(":lib:cryptoaes"))
}
