import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LuvEvaLand"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://luvevalands2.co")
        }
    }

    deeplink {
        path("/truyen-tranh/..*")
    }
}
