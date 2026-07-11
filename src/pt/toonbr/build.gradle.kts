import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ToonBr"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://beta.toonbr.com"
    }
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
