import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "XCOMIC"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    val sources = listOf(
        mapOf("lang" to "all"),
        mapOf("lang" to "ab"),
        mapOf("lang" to "af"),
        mapOf("lang" to "am"),
        mapOf("lang" to "ar"),
        mapOf("lang" to "az"),
        mapOf("lang" to "be"),
        mapOf("lang" to "bg"),
        mapOf("lang" to "bn"),
        mapOf("lang" to "bs"),
        mapOf("lang" to "ca"),
        mapOf("lang" to "ceb"),
        mapOf("lang" to "cs"),
        mapOf("lang" to "cv"),
        mapOf("lang" to "da"),
        mapOf("lang" to "de"),
        mapOf("lang" to "el"),
        mapOf("lang" to "en"),
        mapOf("lang" to "eo"),
        mapOf("lang" to "es"),
        mapOf("lang" to "es-419"),
        mapOf("lang" to "et"),
        mapOf("lang" to "eu"),
        mapOf("lang" to "fa"),
        mapOf("lang" to "fi"),
        mapOf("lang" to "fil"),
        mapOf("lang" to "fo"),
        mapOf("lang" to "fr"),
        mapOf("lang" to "ga"),
        mapOf("lang" to "gn"),
        mapOf("lang" to "gu"),
        mapOf("lang" to "ha"),
        mapOf("lang" to "he"),
        mapOf("lang" to "hi"),
        mapOf("lang" to "hr"),
        mapOf("lang" to "ht"),
        mapOf("lang" to "hu"),
        mapOf("lang" to "hy"),
        mapOf("lang" to "id"),
        mapOf("lang" to "ig"),
        mapOf("lang" to "is"),
        mapOf("lang" to "it"),
        mapOf("lang" to "ja"),
        mapOf("lang" to "jv"),
        mapOf("lang" to "ka"),
        mapOf("lang" to "kk"),
        mapOf("lang" to "km"),
        mapOf("lang" to "kn"),
        mapOf("lang" to "ko"),
        mapOf("lang" to "ku"),
        mapOf("lang" to "ky"),
        mapOf("lang" to "lb"),
        mapOf("lang" to "lo"),
        mapOf("lang" to "lt"),
        mapOf("lang" to "lv"),
        mapOf("lang" to "mg"),
        mapOf("lang" to "mi"),
        mapOf("lang" to "mk"),
        mapOf("lang" to "ml"),
        mapOf("lang" to "mn"),
        mapOf("lang" to "mo"),
        mapOf("lang" to "mr"),
        mapOf("lang" to "ms"),
        mapOf("lang" to "mt"),
        mapOf("lang" to "my"),
        mapOf("lang" to "ne"),
        mapOf("lang" to "nl"),
        mapOf("lang" to "no"),
        mapOf("lang" to "ny"),
        mapOf("lang" to "pl"),
        mapOf("lang" to "ps"),
        mapOf("lang" to "pt"),
        mapOf("lang" to "pt-BR"),
        mapOf("lang" to "rm"),
        mapOf("lang" to "ro"),
        mapOf("lang" to "ru"),
        mapOf("lang" to "sd"),
        mapOf("lang" to "sh"),
        mapOf("lang" to "si"),
        mapOf("lang" to "sk"),
        mapOf("lang" to "sl"),
        mapOf("lang" to "sm"),
        mapOf("lang" to "sn"),
        mapOf("lang" to "so"),
        mapOf("lang" to "sq"),
        mapOf("lang" to "sr"),
        mapOf("lang" to "st"),
        mapOf("lang" to "stsr"),
        mapOf("lang" to "sv"),
        mapOf("lang" to "sw"),
        mapOf("lang" to "ta"),
        mapOf("lang" to "te"),
        mapOf("lang" to "tg"),
        mapOf("lang" to "th"),
        mapOf("lang" to "ti"),
        mapOf("lang" to "tk"),
        mapOf("lang" to "to"),
        mapOf("lang" to "tr"),
        mapOf("lang" to "uk"),
        mapOf("lang" to "ur"),
        mapOf("lang" to "uz"),
        mapOf("lang" to "vi"),
        mapOf("lang" to "yo"),
        mapOf("lang" to "zh"),
        mapOf("lang" to "zh-Hans"),
        mapOf("lang" to "zh-Hant"),
        mapOf("lang" to "zu"),
        mapOf("lang" to "other"),
    )

    sources.forEach { s ->
        source {
            lang = s["lang"] as String
            baseUrl {
                mirrors(
                    "https://xcomic.me",
                    "https://xcomic.net",
                )
            }
        }
    }

    deeplink {
        host("xcomic.me")
        host("xcomic.net")
        path("/comic/..*")
    }
}

dependencies {
    implementation(project(":lib:cryptoaes"))
}
