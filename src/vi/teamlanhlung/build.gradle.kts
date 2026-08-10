import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Team Lanh Lung"
    versionCode = 36
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "Team Lạnh Lùng"
        lang = "vi"
        baseUrl {
            custom("https://lanhlungteam2.top")
        }
    }

    deeplink {
        path("/.*")
    }
}
