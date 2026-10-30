package com.local.secrename.shared

import kotlin.test.*

class DocumentRulesTest {
    @Test fun multilineHolder() = assertEquals(listOf("NORTHSTAR TRADING CORPORATION"), DocumentRules.candidates("This is to certify that\nNORTHSTAR TRADING\nCORPORATION\nhas been registered"))
    @Test fun noUnanchoredGuess() = assertTrue(DocumentRules.candidates("Submitted by OTHER COMPANY INC.").isEmpty())
    @Test fun ambiguousHolders() = assertEquals(2, DocumentRules.candidates("This is to certify that\nALPHA INC.\nThis is to certify that\nBETA INC.").size)
    @Test fun duplicateCaseAndGap() = assertEquals("ACME (2).pdf", DocumentRules.allocate("ACME", ".pdf", listOf("acme.PDF", "ACME (1).pdf")))
    @Test fun safeName() = assertEquals("ACME SERVICES", DocumentRules.safeStem("ACME / SERVICES..."))
    @Test fun rejectBlank() { assertFailsWith<IllegalArgumentException> { DocumentRules.safeStem("...") } }
    @Test fun windowsReserved() = assertEquals("_CON", DocumentRules.safeStem("CON"))

    private val header = "CERTIFICATE OF INCORPORATION\nKNOW ALL PERSONS BY THESE PRESENTS:\n"

    @Test fun articlesAndBylawsClause() = assertEquals(listOf("SILVERBAY MERIDIAN TRADING CORPORATION"), DocumentRules.candidates(header + "This simulated certificate depicts the Articles of Incorporation and By-Laws of:\nSILVERBAY MERIDIAN TRADING CORPORATION\nwhich, in an authentic certificate, would appear with details."))
    @Test fun presentationWithoutIntroductoryClause() = assertEquals(listOf("NORTHVALE INFRASTRUCTURE SOLUTIONS INC."), DocumentRules.candidates(header + "NORTHVALE INFRASTRUCTURE SOLUTIONS INC.\nwhich has been registered."))
    @Test fun legacyPresentationWording() = assertEquals(listOf("ACME INC."), DocumentRules.candidates("CERTIFICATE OF INCORPORATION\nKNOW ALL MEN BY THESE PRESENTS:\nACME INC.\nwhich has been registered."))
    @Test fun optionalOcrSuffixPeriod() = assertEquals(listOf("ACME INC"), DocumentRules.candidates(header + "ACME INC\nwhich has been registered."))
    @Test fun multilinePresentationName() = assertEquals(listOf("NORTHVALE INFRASTRUCTURE SOLUTIONS INC."), DocumentRules.candidates(header + "NORTHVALE INFRASTRUCTURE\nSOLUTIONS INC.\nwhich has been registered."))
    @Test fun articlesNeedsCertificateContext() = assertTrue(DocumentRules.candidates("Notes about the Articles of Incorporation and By-Laws of:\nOTHER INC.").isEmpty())
    @Test fun presentationNeedsCertificateContext() = assertTrue(DocumentRules.candidates("KNOW ALL PERSONS BY THESE PRESENTS:\nOTHER INC.").isEmpty())
    @Test fun twoDisplayNamesRemainAmbiguous() = assertEquals(listOf("ALPHA INC.", "BETA CORPORATION"), DocumentRules.candidates(header + "ALPHA INC.\nBETA CORPORATION\nwhich has been registered."))
    @Test fun noContextFromPreviousPage() = assertTrue(DocumentRules.candidates("CERTIFICATE OF INCORPORATION\u000cKNOW ALL PERSONS BY THESE PRESENTS:\nOTHER INC.").isEmpty())
    @Test fun bodyCompanyIsNotHolder() = assertTrue(DocumentRules.candidates(header + "which has been registered.\nSubmitted by OTHER INC.").isEmpty())
    @Test fun directClauseSupportsMixedCase() = assertEquals(listOf("Silver Bay Services Inc."), DocumentRules.candidates(header + "Articles of Incorporation and By Laws of:\nSilver Bay Services Inc.\nwhich has been registered."))
    @Test fun sameLineClause() = assertEquals(listOf("ACME INC."), DocumentRules.candidates(header + "Articles of Incorporation of ACME INC., which has been registered."))
    @Test fun rejectBareSuffix() = assertTrue(DocumentRules.candidates(header + "CORPORATION\nwhich has been registered.").isEmpty())

    @Test fun secretaryCompanyBeforeBankResolution() = assertEquals(listOf("ABC SOLUTIONS INC."), DocumentRules.candidates("SECRETARY’S CERTIFICATE\nI am the duly elected Assistant Corporate Secretary of ABC SOLUTIONS INC. (the “Corporation”),\nDO HEREBY CERTIFY THAT:\nRESOLVED, to receive payments from PHILIPPINE SAVINGS BANK."))
    @Test fun secretaryMultilineRoleAndName() = assertEquals(listOf("SUNRIDGE INDUSTRIES INC."), DocumentRules.candidates("SECRETARY'S CERTIFICATE\nI am incumbent Assistant\nCorporate Secretary of SUNRIDGE\nINDUSTRIES INC. (the \"Corporation\"),\nDO HEREBY CERTIFY THAT:\nPHILIPPINE NATIONAL BANK"))
    @Test fun secretaryRoleRequiresDocumentHeading() = assertTrue(DocumentRules.candidates("Letter from the Corporate Secretary of OTHER INC. (the Corporation)").isEmpty())
    @Test fun bankMentionAloneDoesNotIdentifyCompany() = assertTrue(DocumentRules.candidates("SECRETARY'S CERTIFICATE\nDO HEREBY CERTIFY THAT:\nRESOLVED, authorize payment from OTHER SERVICES INC.").isEmpty())
    @Test fun laterSecretaryRoleDoesNotOverrideHolder() = assertEquals(listOf("OWNER INC."), DocumentRules.candidates("SECRETARY'S CERTIFICATE\nCorporate Secretary of OWNER INC. (the Corporation)\nDO HEREBY CERTIFY THAT:\nRESOLVED, notify the Corporate Secretary of OTHER INC. (the Company)."))
    @Test fun secretaryNameMayContainParentheses() = assertEquals(listOf("OWNER (PHILIPPINES) INC."), DocumentRules.candidates("SECRETARY'S CERTIFICATE\nCorporate Secretary of OWNER (PHILIPPINES) INC. (the Corporation)\nDO HEREBY CERTIFY THAT:"))
    @Test fun twoSecretaryDeclarationsRemainAmbiguous() = assertEquals(listOf("FIRST INC.", "SECOND INC."), DocumentRules.candidates("SECRETARY'S CERTIFICATE\nCorporate Secretary of FIRST INC. (the Corporation)\nCorporate Secretary of SECOND INC. (the Company)\nDO HEREBY CERTIFY THAT:"))
    @Test fun secretaryWithoutCompanyAnchorRequiresReview() = assertTrue(DocumentRules.candidates("SECRETARY'S CERTIFICATE\nSUNRIDGE INDUSTRIES INC.\nDO HEREBY CERTIFY THAT:").isEmpty())
}
