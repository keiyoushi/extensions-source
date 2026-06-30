plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "92Manhua"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "sinmh"

    source {
	    name = "92漫画"
        lang = "zh"
        baseUrl = "http://www.92mh.com"
    }
}
