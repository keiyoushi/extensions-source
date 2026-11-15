package eu.kanade.tachiyomi.extension.ar.alkhariqun

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class Category(val name: String, val id: String)

class AuthorFilter : Filter.Text("Author")

class CategoryCheckBox(name: String, val id: String) : Filter.CheckBox(name)

class CategoryFilter(categories: List<Category>) : Filter.Group<CategoryCheckBox>("Categories", categories.map { CategoryCheckBox(it.name, it.id) })
