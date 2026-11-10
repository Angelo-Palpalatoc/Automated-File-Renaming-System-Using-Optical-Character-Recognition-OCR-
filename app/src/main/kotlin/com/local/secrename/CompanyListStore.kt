package com.local.secrename

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class SavedCompanyList(val sourceName: String, val companies: List<String>)

/** Stores only the validated names in app-private storage, never the source workbook. */
class CompanyListStore(private val file: File) {
    @Synchronized fun load(): SavedCompanyList? {
        if (!file.exists()) return null
        if (file.length() > 4 * 1024 * 1024) throw IOException("The saved company list is invalid.")
        return DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readUTF() != "SEC_COMPANIES_1") throw IOException("The saved company list is invalid.")
            val name = input.readUTF()
            val count = input.readInt()
            if (count !in 1..CompanyListReader.MAX_COMPANIES) throw IOException("The saved company list is invalid.")
            val list = SavedCompanyList(name, List(count) { input.readUTF() })
            validate(list)
            if (input.read() != -1) throw IOException("The saved company list is invalid.")
            list
        }
    }

    @Synchronized fun save(list: SavedCompanyList) {
        validate(list)
        val parent = file.absoluteFile.parentFile ?: throw IOException("No storage directory.")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Could not create company-list storage.")
        val temporary = File.createTempFile("company-list-", ".tmp", parent)
        try {
            FileOutputStream(temporary).use { stream ->
                val output = DataOutputStream(stream)
                output.writeUTF("SEC_COMPANIES_1")
                output.writeUTF(list.sourceName)
                output.writeInt(list.companies.size)
                list.companies.forEach(output::writeUTF)
                output.flush()
                stream.fd.sync()
            }
            // Both paths are in the same private directory; a failure leaves the old list intact.
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }

    @Synchronized fun clear() {
        if (file.exists() && !file.delete()) throw IOException("Could not remove the company list.")
    }

    private fun validate(list: SavedCompanyList) {
        require(list.sourceName.isNotBlank() && list.sourceName.length <= 255) { "Invalid company-list filename." }
        require(list.companies.size in 1..CompanyListReader.MAX_COMPANIES) { "Invalid company-list size." }
        require(list.companies.all { it.isNotBlank() && it.length <= 180 && it.none(Char::isISOControl) }) { "Invalid company name." }
    }
}
