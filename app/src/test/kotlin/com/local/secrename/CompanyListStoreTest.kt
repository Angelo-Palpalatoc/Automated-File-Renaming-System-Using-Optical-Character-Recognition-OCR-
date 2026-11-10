package com.local.secrename

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CompanyListStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun remembersNamesAcrossInstancesAndCanRemoveThem() {
        val file = File(folder.root, "company-list.bin")
        val list = SavedCompanyList("companies.xlsx", listOf("ABC SOLUTIONS INC.", "MÜLLER & SONS INC."))
        assertNull(CompanyListStore(file).load())
        CompanyListStore(file).save(list)
        assertEquals(list, CompanyListStore(file).load())
        CompanyListStore(file).clear()
        assertNull(CompanyListStore(file).load())
    }

    @Test fun invalidReplacementDoesNotChangePreviouslySavedList() {
        val file = File(folder.root, "company-list.bin")
        val store = CompanyListStore(file)
        val original = SavedCompanyList("companies.xlsx", listOf("ABC SOLUTIONS INC."))
        store.save(original)
        try { store.save(SavedCompanyList("empty.xlsx", emptyList())); fail("Expected invalid list") }
        catch (_: IllegalArgumentException) { }
        assertEquals(original, store.load())
        assertEquals(listOf("company-list.bin"), folder.root.list()?.toList())
    }

    @Test fun validReplacementIsAtomicAndLeavesNoWorkbookOrTemporaryFile() {
        val file = File(folder.root, "company-list.bin")
        val store = CompanyListStore(file)
        store.save(SavedCompanyList("first.xlsx", listOf("ABC SOLUTIONS INC.")))
        val updated = SavedCompanyList("second.xlsx", listOf("SUNRIDGE INDUSTRIES INC."))
        store.save(updated)
        assertEquals(updated, store.load())
        assertEquals(listOf("company-list.bin"), folder.root.list()?.toList())
    }

    @Test fun truncatedStoreIsReportedInsteadOfUsed() {
        val file = File(folder.root, "company-list.bin")
        file.writeBytes(byteArrayOf(0, 10, 1))
        try { CompanyListStore(file).load(); fail("Expected corrupt list") }
        catch (_: java.io.IOException) { }
    }
}
