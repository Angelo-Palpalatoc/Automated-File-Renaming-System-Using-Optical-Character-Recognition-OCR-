package com.local.secrename.shared

/** A proposal for human review, never an instruction to export. */
data class CompanyMatch(
    val suggestedName: String? = null,
    val evidence: String? = null,
    val ambiguous: Boolean = false,
    val matchedFromList: Boolean = false,
)

object CompanyMatcher {
    /** Compare only names found in holder clauses, not arbitrary mentions in the document. */
    fun resolve(text: String, companies: List<String>): CompanyMatch {
        val candidates = DocumentRules.candidates(text).distinctBy(::normalize)
        if (candidates.isEmpty()) return CompanyMatch()
        if (candidates.size != 1) return CompanyMatch(ambiguous = true)
        val evidence = candidates.single()
        val key = normalize(evidence)
        val entries = companies.filter { it.isNotBlank() }.distinctBy(::normalize)
        val exact = entries.firstOrNull { normalize(it) == key }
        if (exact != null) return CompanyMatch(exact, evidence, matchedFromList = true)
        val near = entries.filter { nearby(key, normalize(it)) }
        return when (near.size) {
            0 -> CompanyMatch(evidence, evidence)
            1 -> CompanyMatch(near.single(), evidence, matchedFromList = true)
            else -> CompanyMatch(evidence = evidence, ambiguous = true)
        }
    }

    /** Ignore presentation punctuation; preserve word boundaries and legal entity type. */
    fun normalize(value: String): String = value.uppercase()
        .replace(Regex("[.,'’]"), "")
        .replace(Regex("\\s+"), " ").trim()

    private val legalForms = mapOf(
        "INC" to "INC", "INCORPORATED" to "INC", "CORP" to "CORP", "CORPORATION" to "CORP",
        "CO" to "CO", "COMPANY" to "CO", "LTD" to "LTD", "LIMITED" to "LTD",
        "OPC" to "OPC", "PARTNERSHIP" to "PARTNERSHIP",
    )

    private fun nearby(left: String, right: String): Boolean {
        // Never fuzzy-match short identifiers or different legal entity types. Abbreviations
        // of the same type are accepted only when the substantive name also agrees closely.
        val a = left.split(' ')
        val b = right.split(' ')
        if (a.size != b.size || a.size < 2) return false
        val formA = legalForms[a.last()]
        val formB = legalForms[b.last()]
        if (formA != null && formB != null && formA != formB) return false
        val bodyA = a.dropLast(1).joinToString(" ")
        val bodyB = b.dropLast(1).joinToString(" ")
        if (minOf(bodyA.length, bodyB.length) < 8) return false
        // A short changed token may be an acronym, region, or number rather than a typo.
        for (i in 0 until a.lastIndex) {
            if (a[i] != b[i] && (minOf(a[i].length, b[i].length) < 5 || a[i].any(Char::isDigit) || b[i].any(Char::isDigit))) return false
        }
        val limit = if (minOf(bodyA.length, bodyB.length) >= 15) 2 else 1
        val suffixCost = if (formA != null && formA == formB) 0 else distance(a.last(), b.last(), limit)
        if (suffixCost > limit) return false
        return distance(bodyA, bodyB, limit - suffixCost) + suffixCost <= limit
    }

    /** Bounded optimal-string-alignment distance includes one adjacent transposition. */
    private fun distance(a: String, b: String, limit: Int): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var beforePrevious = IntArray(b.length + 1)
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            var minimum = current[0]
            for (j in 1..b.length) {
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    current[j] = minOf(current[j], beforePrevious[j - 2] + 1)
                }
                minimum = minOf(minimum, current[j])
            }
            if (minimum > limit) return limit + 1
            beforePrevious = previous
            previous = current
        }
        return previous[b.length]
    }
}
