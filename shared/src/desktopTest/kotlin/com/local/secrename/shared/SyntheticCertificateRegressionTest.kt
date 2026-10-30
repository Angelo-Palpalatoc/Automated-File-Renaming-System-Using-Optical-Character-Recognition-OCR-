package com.local.secrename.shared

import kotlin.test.*

/** Real Windows OCR transcripts of the user's explicitly synthetic image-only PDF dataset.
 * This verifies extraction from recognized text, not Android ML Kit recognition itself.
 */
class SyntheticCertificateRegressionTest {
    private fun resource(name: String): String = checkNotNull(javaClass.getResource("/synthetic-certificates/$name")) { "Missing fixture $name" }.readText(Charsets.UTF_8)

    @Test fun extractsAllTenSuppliedCertificateLayouts() {
        val rows = resource("expected.tsv").lineSequence().filter { it.isNotBlank() }.toList()
        assertEquals(10, rows.size)
        for (row in rows) {
            val (name, expected) = row.split('\t', limit = 2)
            assertEquals(listOf(expected), DocumentRules.candidates(resource(name)), "Failed on OCR transcript $name")
        }
    }

    @Test fun extractsCompanyFromPhilippineSecretarySample() = assertEquals(listOf("SUNRIDGE INDUSTRIES INC."), DocumentRules.candidates(resource("secretary-philippine.txt")))
    @Test fun extractsCompanyFromSecSecretarySample() = assertEquals(listOf("ABC SOLUTIONS INC."), DocumentRules.candidates(resource("secretary-sec.txt")))
}
