import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Xiutaku"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://xiutaku.com"
    }

    deeplink {
        host("xiutaku.com")
        path("/.*")
    }
}

dependencies {

    implementation(project(":lib:randomua"))
}
