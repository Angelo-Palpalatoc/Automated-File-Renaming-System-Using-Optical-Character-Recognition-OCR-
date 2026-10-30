package com.local.secrename.shared

import kotlin.test.*

/** Original prototype's seven behavioral checks now exercise the single shared Kotlin extractor. */
class LegacyNamingContractTest {
    @Test fun extractsMultilineCertificateHolder() = assertEquals(
        listOf("NORTHSTAR TRADING CORPORATION"),
        DocumentRules.candidates("CERTIFICATE\nThis is to certify that\nNORTHSTAR TRADING\nCORPORATION\nhas been registered"),
    )

    @Test fun refusesUnanchoredCompanyMention() = assertTrue(DocumentRules.candidates("Submitted by SOME COMPANY INC.").isEmpty())

    @Test fun preservesAmbiguity() = assertEquals(2, DocumentRules.candidates("This is to certify that\nALPHA INC.\nThis is to certify that\nBETA INC.").size)

    @Test fun numbersAgainstExistingNamesCaseInsensitively() = assertEquals("ACME (2).pdf", DocumentRules.allocate("ACME", ".pdf", listOf("acme.PDF", "ACME (1).pdf")))

    @Test fun keepsExtensionAndUsesFirstAvailableGap() = assertEquals("ACME (1).png", DocumentRules.allocate("ACME", ".png", listOf("ACME.png", "ACME (2).png")))

    @Test fun cleansUnsafeNames() {
        assertEquals("ACME SERVICES", DocumentRules.safeStem(" ACME / SERVICES... "))
        assertEquals("_CON", DocumentRules.safeStem("CON"))
    }

    @Test fun rejectsEmptyName() { assertFailsWith<IllegalArgumentException> { DocumentRules.safeStem("... ") } }
}
