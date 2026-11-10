package com.local.secrename

import java.io.File

/** Deletes only direct, app-owned working files. Never traverses folders or follows a path outside them. */
internal class PrivateCopies(root: File) {
    private val privateRoot = root.canonicalFile
    private val imports = File(root, "imports").canonicalFile
    private val captures = File(root, "captures").canonicalFile

    private fun inside(file: File, directory: File): Boolean =
        directory.parentFile == privateRoot && file.isFile && file.canonicalFile.parentFile == directory && file.absoluteFile.parentFile?.canonicalFile == directory

    fun retireImport(file: File, protected: Set<File>?): Boolean {
        if (protected == null || !inside(file, imports)) return false
        if (file.canonicalFile in protected.map { it.canonicalFile }) return false
        return file.delete()
    }

    fun sweepImports(protected: Set<File>?) {
        if (protected == null) return
        imports.listFiles()?.forEach { retireImport(it, protected) }
    }

    /** Call only after the output and completed journal have been verified and committed. */
    fun retireVerifiedCapture(file: File, sourceHash: String): Boolean {
        if (!inside(file, captures)) return false
        if (file.inputStream().use { CopyIntegrity.sha256(it) } != sourceHash) return false
        return file.delete()
    }
}
