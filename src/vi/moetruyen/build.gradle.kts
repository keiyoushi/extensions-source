import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

dependencies {
    implementation(project(":lib:ece"))
}

keiyoushi {
    name = "MoeTruyen"
    versionCode = 17
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    deeplink {
        path("/manga/.*")
    }

    source {
        lang = "vi"
        baseUrl {
            mirrors(
                "https://moetruyen.net",
                "https://truyen.moe",
            )
        }
    }
}
