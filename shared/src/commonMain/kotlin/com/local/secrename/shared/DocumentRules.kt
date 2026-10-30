package com.local.secrename.shared

/** Platform-neutral naming rules. A candidate is evidence for review, not authorization. */
object DocumentRules {
    private val certifyAnchor = Regex("this\\s+is\\s+to\\s+certify\\s+that\\s*[:,-]?\\s*", RegexOption.IGNORE_CASE)
    private val articlesAnchor = Regex("articles\\s+of\\s+(?:incorporation|partnership)(?:\\s+(?:and|&)\\s+by[\\s-]*laws)?\\s+of\\s*:?\\s*", RegexOption.IGNORE_CASE)
    private val presentationAnchor = Regex("know\\s+all\\s+(?:persons|men)\\s+by\\s+these\\s+presents\\s*:?\\s*", RegexOption.IGNORE_CASE)
    private val certificateHeading = Regex("certificate\\s+of\\s+(?:incorporation|registration|partnership)\\b", RegexOption.IGNORE_CASE)
    private val secretaryHeading = Regex("secretary[’']?s?\\s+certificate\\b", RegexOption.IGNORE_CASE)
    private val secretaryRole = Regex("\\b(?:assistant\\s+)?(?:corporate\\s+)?secretary\\s+of\\s+", RegexOption.IGNORE_CASE)
    private val secretaryStatement = Regex("\\b(?:do\\s+hereby\\s+certify|resolved|in\\s+witness|subscribed)\\b", RegexOption.IGNORE_CASE)
    private val companyAlias = Regex("\\(\\s*(?:the\\s+)?[\"'“”‘’]*(?:corporation|company)\\b", RegexOption.IGNORE_CASE)
    private val end = Regex("\\b(which|has been|is hereby|was registered|having complied|a corporation|a partnership|this example|this sample)\\b", RegexOption.IGNORE_CASE)
    private val suffix = Regex("\\b(incorporated|corporation|company|inc\\.?|corp\\.?|co\\.?|limited|ltd\\.?|partnership)\\s*$", RegexOption.IGNORE_CASE)

    fun candidates(text: String): List<String> = buildList {
        // Process pages separately so a heading on one page cannot authorize another page's name.
        for (page in text.split('\u000c')) {
            certifyAnchor.findAll(page).forEach { match -> readDirectName(page.substring(match.range.last + 1))?.let { add(it) } }
            if (secretaryHeading.containsMatchIn(page)) addAll(readSecretaryNames(page))
            if (!certificateHeading.containsMatchIn(page)) continue
            articlesAnchor.findAll(page).forEach { match -> readDirectName(page.substring(match.range.last + 1))?.let { add(it) } }
            presentationAnchor.findAll(page).forEach { match -> addAll(readPresentationNames(page.substring(match.range.last + 1))) }
        }
    }.distinctBy { it.lowercase().replace(Regex("\\s+"), " ") }

    private fun readSecretaryNames(page: String): List<String> = buildList {
        // The introductory role identifies the company represented by the secretary.
        // Banks and authorized representatives named later in resolutions are not holders.
        val introEnd = secretaryStatement.find(page)?.range?.first ?: page.length
        val introduction = page.take(introEnd).take(2500)
        secretaryRole.findAll(introduction).forEach { role ->
            val tail = introduction.substring(role.range.last + 1)
            val alias = companyAlias.find(tail)
            val nameBlock = if (alias == null) tail else tail.take(alias.range.first)
            readDirectName(nameBlock)?.let { add(it) }
        }
    }

    private fun readDirectName(region: String): String? {
        val lines = region.lineSequence()
        val collected = mutableListOf<String>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val terminator = end.find(line)
            val part = (if (terminator != null) line.substring(0, terminator.range.first) else line).trim().trimEnd(',', ';')
            if (part.isNotEmpty()) collected += part
            val name = collected.joinToString(" ")
            if (terminator != null || suffix.containsMatchIn(name) || name.length > 180 || collected.size >= 4) break
        }
        return collected.joinToString(" ").replace(Regex("\\s+"), " ").takeIf(::plausibleName)
    }

    private fun readPresentationNames(region: String): List<String> = buildList {
        val collected = mutableListOf<String>()
        for (raw in region.lineSequence().take(8)) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val terminator = end.find(line)
            val part = (if (terminator != null) line.substring(0, terminator.range.first) else line).trim().trimEnd(',', ';')
            val letters = part.filter { it.isLetter() }
            // Layout fallback needs a company-like display block in a bounded certificate clause,
            // not merely an uppercase company mention somewhere in the document.
            val displayBlock = letters.isNotEmpty() && letters.count { it.isUpperCase() }.toDouble() / letters.length >= 0.8
            if (!displayBlock || part.length > 180) collected.clear()
            else {
                collected += part
                val value = collected.joinToString(" ")
                if (plausibleName(value)) { add(value); collected.clear() }
                else if (collected.size >= 4 || value.length > 180) collected.clear()
            }
            if (terminator != null) break
        }
    }

    private fun plausibleName(value: String): Boolean {
        if (value.length !in 4..180 || !suffix.containsMatchIn(value)) return false
        // A bare legal suffix or a complete prose sentence is not a certificate-holder name.
        val stem = value.replace(suffix, "").trim()
        return stem.any { it.isLetterOrDigit() } && !Regex("[!?;:]|\\b(?:certificate|certifies|certify|submitted|issued|registered)\\b", RegexOption.IGNORE_CASE).containsMatchIn(value)
    }

    fun safeStem(name: String): String {
        var value = name.map { if (it.code < 32 || it in "<>:\"/\\|?*") ' ' else it }.joinToString("")
            .replace(Regex("\\s+"), " ").trim().trimEnd('.', ' ')
        require(value.isNotEmpty()) { "Enter a verified company name." }
        require(value.length <= 140) { "Shorten the output name to 140 characters or fewer." }
        if (Regex("(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?", RegexOption.IGNORE_CASE).matches(value)) value = "_$value"
        return value
    }

    fun allocate(name: String, extension: String, existing: Collection<String>): String {
        val stem = safeStem(name)
        require(extension.startsWith('.') && extension.drop(1).all { it.isLetterOrDigit() }) { "Unsupported extension." }
        val occupied = existing.map { it.lowercase() }.toSet()
        for (index in 0..99999) {
            val proposal = stem + (if (index == 0) "" else " ($index)") + extension
            if (proposal.lowercase() !in occupied) return proposal
        }
        error("Too many duplicates in this batch.")
    }
}
