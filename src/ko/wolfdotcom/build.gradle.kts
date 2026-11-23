import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Wolf.com"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "늑대닷컴 - 웹툰"
        lang = "ko"
        baseUrl {
            mirrors(
                "늑대닷컴" to "https://wfwf544.com",
                "늑대닷컴2" to "https://wftoon238.com",
                "여우코믹스" to "https://fxfx332.com",
            )
        }
    }

    source {
        name = "늑대닷컴 - 만화책"
        lang = "ko"
        baseUrl {
            mirrors("늑대닷컴" to "https://wfwf544.com")
        }
    }

    source {
        name = "늑대닷컴 - 포토툰"
        lang = "ko"
        baseUrl {
            mirrors(
                "늑대닷컴2" to "https://wftoon238.com",
                "여우코믹스" to "https://fxfx332.com",
            )
        }
    }

    deeplink {
        path("/list")
        path("/view")
        path("/cl")
        path("/cv")
    }
}
