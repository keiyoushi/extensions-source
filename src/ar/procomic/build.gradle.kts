import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Procomic"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl {
            mirrors(
                "Main" to "https://procomic.pro",
                "Mirror" to "https://procomic.net",
            )
        }
        lang = "ar"
        versionId = 6
    }

    deeplink {
        path("/..*")
    }
}

dependencies {
    implementation(project(":lib:cookieinterceptor"))
    compileOnlyApi("com.github.tachiyomiorg:image-decoder:e08e9be535")
}
