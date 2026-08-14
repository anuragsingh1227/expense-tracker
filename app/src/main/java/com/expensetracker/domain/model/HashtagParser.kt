package com.expensetracker.domain.model

/**
 * Inline `#hashtags` in transaction notes, plus chip-selected tags.
 * Tags are local-only labels (e.g. `#Reimbursable`, `#TripToGoa`).
 */
object HashtagParser {

    val SUGGESTED = listOf("Reimbursable", "Business", "Personal", "Family")

    private val TAG = Regex("""#([A-Za-z][A-Za-z0-9_]{0,31})""")

    fun extract(notes: String?): List<String> =
        TAG.findAll(notes.orEmpty())
            .map { it.groupValues[1] }
            .map(::canonicalize)
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .toList()

    fun canonicalize(raw: String): String =
        raw.trim().trimStart('#').replace(Regex("""\s+"""), "")

    fun merge(notes: String?, chips: Iterable<String>): List<String> =
        (extract(notes) + chips.map(::canonicalize))
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    fun containsTag(tags: Collection<String>, query: String): Boolean {
        val needle = canonicalize(query).lowercase()
        if (needle.isEmpty()) return false
        return tags.any { it.lowercase() == needle }
    }
}
