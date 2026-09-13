import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "PureSkill"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "PureSkill"
        lang = "uk"
        baseUrl = "https://pure-skill.pages.dev"
    }

    deeplink {
        path("/chapters..*")
        path("/reader..*")
    }
}
