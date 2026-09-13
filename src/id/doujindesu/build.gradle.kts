import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Doujindesu"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl {
            custom("https://doujin.desu.xxx")
        }
        id = 7704282043609669342L
    }

    deeplink {
        path("/manga/..*")
        path("/reader/..*")
    }
}

dependencies {
    implementation(project(":lib:randomua"))
}
