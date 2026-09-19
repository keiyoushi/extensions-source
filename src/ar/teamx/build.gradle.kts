import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

dependencies {
    implementation(project(":lib:randomua"))
}

keiyoushi {
    name = "Team X"
    versionCode = 35
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "ar"
        baseUrl {
            custom("https://olympustaff.com")
        }
    }

    deeplink {
        path("/series/..*")
    }
}
