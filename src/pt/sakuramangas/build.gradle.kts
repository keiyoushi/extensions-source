import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sakura Mangás"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "pt-BR"
        baseUrl = "https://sakuramangas.org"
    }

    deeplink {
        host("sakuramangas.org")
        host("www.sakuramangas.org")
        path("/obras/..*")
    }
}

dependencies {
    implementation(project(":lib:cronet"))
}
