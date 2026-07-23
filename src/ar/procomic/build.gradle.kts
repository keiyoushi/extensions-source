import io.github.keiyoushi.gradle.api.ContentWarning
import org.gradle.kotlin.dsl.dependencies

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "procomic"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl = "https://procomic.pro"
        lang = "ar"
    }

    deeplink {
        path("/..*")
    }
}

dependencies {
    implementation(project(":lib:cookieinterceptor"))
}
