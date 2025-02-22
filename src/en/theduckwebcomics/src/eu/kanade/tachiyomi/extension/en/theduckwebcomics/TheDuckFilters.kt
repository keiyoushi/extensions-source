package eu.kanade.tachiyomi.extension.en.theduckwebcomics

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

class Label(name: String, val value: String) : Filter.CheckBox(name)

interface QueryParam {
    val param: String

    fun encode(url: HttpUrl.Builder)
}

sealed class LabelGroup(
    name: String,
    values: List<Label>,
) : QueryParam, Filter.Group<Label>(name, values) {
    override fun encode(url: HttpUrl.Builder) =
        state.filter { it.state }.forEach {
            url.addQueryParameter(param, it.value)
        }
}

class TypeFilter(
    values: List<Label> = types,
) : LabelGroup("Type of comic", values) {
    override val param = "type"

    companion object {
        private val types: List<Label>
            get() = listOf(
                Label("Comic Strip", "0"),
                Label("Comic Book/Story", "1"),
            )
    }
}

class ToneFilter(
    values: List<Label> = tones,
) : LabelGroup("Tone", values) {
    override val param = "tone"

    companion object {
        private val tones: List<Label>
            get() = listOf(
                Label("Comedy", "0"),
                Label("Drama", "1"),
                // Label("N/A", "2"),
                Label("Other", "3"),
            )
    }
}

class StyleFilter(
    values: List<Label> = styles,
) : LabelGroup("Art style", values) {
    override val param = "style"

    companion object {
        private val styles: List<Label>
            get() = listOf(
                Label("Cartoon", "0"),
                Label("American", "1"),
                Label("Manga", "2"),
                Label("Realism", "3"),
                Label("Sprite", "4"),
                Label("Sketch", "5"),
                Label("Experimental", "6"),
                Label("Photographic", "7"),
                Label("Stick Figure", "8"),
            )
    }
}

class GenreFilter(
    values: List<Label> = genres,
) : LabelGroup("Genre", values) {
    override val param = "genre"

    companion object {
        private val genres: List<Label>
            get() = listOf(
                Label("Fantasy", "0"),
                Label("Parody", "1"),
                Label("Real Life", "2"),
                Label("Sci-Fi", "4"),
                Label("Horror", "5"),
                Label("Abstract", "6"),
                Label("Adventure", "8"),
                Label("Noir", "9"),
                // Label("N/A", "10"),
                // Label("N/A", "11"),
                Label("Political", "12"),
                Label("Spiritual", "13"),
                Label("Romance", "14"),
                Label("Superhero", "15"),
                Label("Western", "16"),
                Label("Mystery", "17"),
                Label("War", "18"),
                Label("Tribute", "19"),
            )
    }
}

class RatingFilter(
    values: List<Label> = ratings,
) : LabelGroup("Rating", values) {
    override val param = "rating"

    companion object {
        private val ratings: List<Label>
            get() = listOf(
                Label("Everyone", "E"),
                Label("Teen", "T"),
                Label("Mature", "M"),
                Label("Adult", "A"),
            )
    }
}

class UpdateFilter(
    values: Array<String> = labels.keys.toTypedArray(),
) : QueryParam, Filter.Select<String>("Last update", values) {
    override val param = "last_update"

    override fun encode(url: HttpUrl.Builder) {
        url.addQueryParameter(param, labels[values[state]])
    }

    companion object {
        private val labels = mapOf(
            "Any" to "",
            "Today" to "today",
            "Last week" to "week",
            "Last month" to "month",
        )
    }
}
