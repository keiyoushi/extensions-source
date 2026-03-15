import com.diffplug.spotless.FormatterFunc
import com.diffplug.spotless.FormatterStep
import java.io.Serializable

object RandomUaCheck {
    fun create(): FormatterStep = FormatterStep.create(
        "randomua-requires-getMangaUrl",
        State(),
        State::toFormatter,
    )

    private class State : Serializable {
        fun toFormatter() = FormatterFunc { content ->
            if ("package keiyoushi.lib.randomua" !in content &&
                "keiyoushi.lib.randomua" in content &&
                "override fun getMangaUrl(" !in content
            ) {
                throw AssertionError(
                    "usage of :lib:randomua requires override of getMangaUrl()",
                )
            }
            content
        }
    }
}
