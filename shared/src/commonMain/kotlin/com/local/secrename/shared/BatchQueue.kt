package com.local.secrename.shared

/** Durable, deterministic bookkeeping for a source folder batch. */
data class BatchFile(val id: String, val name: String, val mime: String, val status: BatchStatus = BatchStatus.PENDING, val savedName: String? = null, val issue: String? = null)
enum class BatchStatus { PENDING, SAVED, ALREADY_PROCESSED }

data class BatchProgress(val firstVisible: Int, val lastVisible: Int, val total: Int, val saved: Int)
data class BatchReview(val company: String = "", val evidence: String = "", val issue: String? = null, val analyzing: Boolean = true, val saving: Boolean = false, val previewOpen: Boolean = false)

data class BatchQueue(val sourceId: String, val outputId: String, val files: List<BatchFile>, val pageSize: Int = 10) {
    init { require(pageSize in 1..50); require(files.map { it.id }.distinct().size == files.size) }
    val total get() = files.size
    val savedCount get() = files.count { it.status == BatchStatus.SAVED }
    val finishedCount get() = files.count { it.status != BatchStatus.PENDING }
    val pending get() = files.filter { it.status == BatchStatus.PENDING }
    val visible get() = pending.take(pageSize)
    val hasMore get() = pending.size > pageSize
    val progress get() = BatchProgress(if (total == 0) 0 else (savedCount + 1).coerceAtMost(total), (savedCount + pageSize).coerceAtMost(total), total, savedCount)
    fun markSaved(id: String, savedName: String): BatchQueue {
        require(savedName.isNotBlank())
        require(files.any { it.id == id && it.status == BatchStatus.PENDING })
        return copy(files = files.map { if (it.id == id) it.copy(status = BatchStatus.SAVED, savedName = savedName, issue = null) else it })
    }
    fun reconcileCompleted(
        completedIds: Set<String>, alreadyProcessedIds: Set<String>, checks: Map<String, OutputCheck>,
    ): BatchQueue = copy(files = files.map { file ->
        if (file.id !in completedIds) file.copy(status = BatchStatus.PENDING)
        else when {
            checks[file.id]?.match != null -> file.copy(
                status = if (file.id in alreadyProcessedIds) BatchStatus.ALREADY_PROCESSED else BatchStatus.SAVED,
            )
            checks[file.id]?.needsInspection == true -> file.copy(status = BatchStatus.SAVED)
            else -> file.copy(status = BatchStatus.PENDING, issue = null)
        }
    })
    fun naturalOrder(): BatchQueue = copy(files = files.sortedWith { left, right -> naturalCompare(left.name, right.name) })
    companion object {
        fun create(sourceId: String, outputId: String, entries: List<BatchFile>) = BatchQueue(sourceId, outputId, entries).naturalOrder()
    }
}

private fun naturalCompare(left: String, right: String): Int {
    var a = 0
    var b = 0
    while (a < left.length && b < right.length) {
        val ca = left[a]
        val cb = right[b]
        if (ca.isDigit() && cb.isDigit()) {
            val startA = a; val startB = b
            while (a < left.length && left[a] == '0') a++
            while (b < right.length && right[b] == '0') b++
            val digitsA = a; val digitsB = b
            while (a < left.length && left[a].isDigit()) a++
            while (b < right.length && right[b].isDigit()) b++
            val lengthCompare = (a - digitsA).compareTo(b - digitsB)
            if (lengthCompare != 0) return lengthCompare
            val numberCompare = left.substring(digitsA, a).compareTo(right.substring(digitsB, b))
            if (numberCompare != 0) return numberCompare
            val zeroCompare = (digitsA - startA).compareTo(digitsB - startB)
            if (zeroCompare != 0) return zeroCompare
        } else {
            val compare = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (compare != 0) return compare
            a++; b++
        }
    }
    return (left.length - a).compareTo(right.length - b).takeIf { it != 0 } ?: left.compareTo(right, ignoreCase = true)
}
