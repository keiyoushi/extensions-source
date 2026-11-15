import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Fairies vs Tentacles"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://eldrikdgt.com"
    }

    deeplink {
        path("/fvt/main/.*")
        path("/fvt/qna/.*")
        path("/fvt/latest/.*")
        path("/fvt/questions/.*")
    }
}

dependencies {
    implementation(project(":lib:textinterceptor"))
}
