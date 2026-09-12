import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Million"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    listOf(
        "en", "zh-TW", "th", "fr", "ko-KR", "es", "it", "de", "pt-BR", "es-MX", "zh-CN", "es-AR", "pl", "vi", "el",
        "sv", "tl", "id", "hi", "km", "tr", "zh-HK", "cs", "ru", "pt", "ar", "ca", "zu", "hu", "fi", "sr", "ro", "ms",
        "nl", "da", "no", "af", "am", "as", "be", "bg", "bho", "bo", "ceb", "cnr", "cy", "doi", "dv", "ee", "et", "eu",
        "ga", "gl", "gn", "gu", "haw", "he", "hmn", "hr", "hy", "ig", "ilo", "is", "ka", "kn", "kok", "la", "lb", "lg",
        "ln", "lo", "lt", "lus", "lv", "mai", "mg", "mi", "mk", "ml", "mn", "mni", "mr", "mt", "mww", "my", "ne",
        "nso", "ny", "or", "pa", "qu", "ro-MD", "rw", "sa", "sd", "si", "sk", "sl", "sm", "sn", "sw", "ta", "te", "ts",
        "uk", "xh", "yi", "yo",
    ).forEach {
        source {
            lang = it
            baseUrl = "https://mangamillion.shueisha.co.jp"
        }
    }
}
