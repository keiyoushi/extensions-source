import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "C'moA"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://www.cmoa.jp"
    }
}

dependencies {
    implementation(project(":lib:speedbinb"))
    implementation(project(":lib:cookieinterceptor"))
}
