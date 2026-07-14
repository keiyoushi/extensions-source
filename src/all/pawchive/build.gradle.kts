import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
	alias(kei.plugins.extension)
}

keiyoushi {
	name = "Pawchive"
	versionCode = 1
	contentWarning = ContentWarning.NSFW
	libVersion = "1.4"
	theme = "pawchive"

	source {
		lang = "all"
		baseUrl = "https://pawchive.pw"
	}
}
