import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Wolf.com"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "늑대닷컴 - 웹툰"
        lang = "ko"
        baseUrl {
            custom("https://wfwf414.com")
        }
    }

    source {
        name = "늑대닷컴 - 만화책"
        lang = "ko"
        baseUrl {
            custom("https://wfwf414.com")
        }
    }

    source {
        name = "늑대닷컴 - 포토툰"
        lang = "ko"
        baseUrl {
            custom("https://wfwf414.com")
        }
    }
}
