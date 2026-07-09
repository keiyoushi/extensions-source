plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Webtoons.com"
    versionCode = 56
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    listOf("en", "id", "th", "es", "fr", "zh-Hant", "de").forEach { langCode ->
        source {
            lang = langCode
            baseUrl = "https://www.webtoons.com"
            when (langCode) {
                // ID was removed as part of the name to be more consistent with other entries
                "id" -> id = 8749627068478740298

                // ID kept due to lang code getting more specific
                "zh-Hant" -> id = 2959982438613576472
            }
        }
    }

    deeplink {
        host("webtoons.com")
        host("www.webtoons.com")
        host("m.webtoons.com")
        path("/.*/.*/.*/..*")
        path("/.*/.*/.*/.*/..*")
    }
}

dependencies {
    implementation(project(":lib:cookieinterceptor"))
    implementation(project(":lib:textinterceptor"))
}
