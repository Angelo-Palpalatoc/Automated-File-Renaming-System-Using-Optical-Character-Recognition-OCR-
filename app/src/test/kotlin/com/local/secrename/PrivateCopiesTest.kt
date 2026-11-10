package com.local.secrename

import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.File

class PrivateCopiesTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun file(path: String) = File(temporary.root, path).apply { requireNotNull(parentFile).mkdirs(); writeText("Fictional document: $path") }
    private fun cleaner() = PrivateCopies(temporary.root)

    @Test fun retiringAnImportPreservesOriginalAndRenamedOutput() {
        val original = file("source/scan.pdf")
        val output = file("output/COMPANY.pdf")
        val working = file("imports/work.pdf")
        assertTrue(cleaner().retireImport(working, emptySet()))
        assertTrue(original.exists())
        assertTrue(output.exists())
    }

    @Test fun generalCleanupNeverDeletesCameraScans() {
        val scan = file("captures/scan.pdf")
        cleaner().sweepImports(emptySet())
        assertTrue(scan.exists())
        assertFalse(cleaner().retireImport(scan, emptySet()))
    }

    @Test fun verifiedCaptureCanBeRemovedAfterMatchingSourceHash() {
        val scan = file("captures/scan.pdf")
        val output = file("output/COMPANY.pdf").apply { writeBytes(scan.readBytes()) }
        val hash = output.inputStream().use { CopyIntegrity.sha256(it) }
        assertTrue(cleaner().retireVerifiedCapture(scan, hash))
        assertTrue(output.exists())
    }

    @Test fun changedCaptureIsRetained() {
        val scan = file("captures/scan.pdf")
        val hash = scan.inputStream().use { CopyIntegrity.sha256(it) }
        scan.appendText(" changed")
        assertFalse(cleaner().retireVerifiedCapture(scan, hash))
        assertTrue(scan.exists())
    }

    @Test fun noCleanupMethodCanDeleteAnExternalOrExportedFile() {
        val original = file("source/scan.pdf")
        val output = file("output/COMPANY.pdf")
        for (document in listOf(original, output, File(temporary.root, "imports/../output/COMPANY.pdf"))) {
            val hash = output.inputStream().use { CopyIntegrity.sha256(it) }
            assertFalse(cleaner().retireImport(document, emptySet()))
            assertFalse(cleaner().retireVerifiedCapture(document, hash))
        }
        assertTrue(original.exists())
        assertTrue(output.exists())
    }

    @Test fun unresolvedExportProtectsItsWorkingCopy() {
        val working = file("imports/work.pdf")
        assertFalse(cleaner().retireImport(working, setOf(working)))
        assertTrue(working.exists())
    }

    @Test fun unreadableProtectionRecordsPreventCleanup() {
        val working = file("imports/work.pdf")
        cleaner().sweepImports(null)
        assertFalse(cleaner().retireImport(working, null))
        assertTrue(working.exists())
    }

    @Test fun aProtectedActiveImportSurvivesWhileUnusedImportsAreRemoved() {
        val active = file("imports/active.pdf")
        val unused = file("imports/unused.pdf")
        cleaner().sweepImports(setOf(active))
        assertTrue(active.exists())
        assertFalse(unused.exists())
    }

    @Test fun cleanupDoesNotTraverseSubfolders() {
        val nested = file("imports/other/scan.pdf")
        cleaner().sweepImports(emptySet())
        assertTrue(nested.exists())
    }
}
