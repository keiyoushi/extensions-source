import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "짭툰 (Jjaptoon)"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "짭툰"
        lang = "ko"
        baseUrl {
            custom("https://www.jjaptoon006.com")
        }
    }
}
