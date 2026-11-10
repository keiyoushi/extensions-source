import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

dependencies {
    implementation(project(":lib:aessiv"))
    implementation(project(":lib:ece"))
}

keiyoushi {
    name = "TuiTruyen"
    versionCode = 7
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://tuitruyen.top")
        }
    }

    deeplink {
        path("/manga/.*")
    }
}
