package eu.kanade.tachiyomi.extension.pt.mundohentai

import eu.kanade.tachiyomi.source.model.Filter

class Tag(val name: String, val slug: String) {
    override fun toString(): String = name
}

internal class TagFilter(tags: Array<Tag>) : Filter.Select<Tag>("Tag", tags)

fun getTags(): Array<Tag> = arrayOf(
    Tag("-- Selecione --", ""),
    Tag("Ahegao", "ahegao"),
    Tag("Anal", "anal"),
    Tag("Biquíni", "biquini"),
    Tag("Chubby", "chubby"),
    Tag("Colegial", "colegial"),
    Tag("Creampie", "creampie"),
    Tag("Dark Skin", "dark-skin"),
    Tag("Dupla Penetração", "dupla-penetracao"),
    Tag("Espanhola", "espanhola"),
    Tag("Exibicionismo", "exibicionismo"),
    Tag("Footjob", "footjob"),
    Tag("Furry", "furry"),
    Tag("Futanari", "futanari"),
    Tag("Grupal", "grupal"),
    Tag("Incesto", "incesto"),
    Tag("Lingerie", "lingerie"),
    Tag("MILF", "milf"),
    Tag("Maiô", "maio"),
    Tag("Masturbação", "masturbacao"),
    Tag("Netorare", "netorare"),
    Tag("Oral", "oral"),
    Tag("Peitinhos", "peitinhos"),
    Tag("Preservativo", "preservativo"),
    Tag("Professora", "professora"),
    Tag("Sex Toys", "sex-toys"),
    Tag("Tentáculos", "tentaculos"),
    Tag("Yaoi", "yaoi"),
)
