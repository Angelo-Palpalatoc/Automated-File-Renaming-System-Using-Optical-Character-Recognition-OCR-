package com.local.secrename.shared

import kotlin.test.*

class CompanyMatcherTest {
    private fun certificate(name: String) = "This is to certify that\n$name\nhas been registered."
    private val abc = "ABC SOLUTIONS INC."
    private val cedar = listOf("CEDARSTONE FOOD DISTRIBUTION CORPORATION", "CEDARSTONE FOOD DISTRIBUTIONS CORPORATION")

    @Test fun exactMatchWinsOverSimilarListEntry() {
        val result = CompanyMatcher.resolve(certificate(cedar[0]), cedar)
        assertEquals(cedar[0], result.suggestedName)
        assertTrue(result.matchedFromList)
        assertFalse(result.ambiguous)
    }
    @Test fun casePunctuationAndWhitespaceArePresentationOnly() {
        assertEquals(abc, CompanyMatcher.resolve(certificate("abc  solutions inc"), listOf(abc)).suggestedName)
    }
    @Test fun missingLetterOffersListSpellingWithDocumentEvidence() {
        val result = CompanyMatcher.resolve(certificate("ABC SOLUTION INC."), listOf(abc))
        assertEquals(abc, result.suggestedName)
        assertEquals("ABC SOLUTION INC.", result.evidence)
        assertTrue(result.matchedFromList)
    }
    @Test fun extraLetterAndTranspositionAreTolerated() {
        assertEquals(abc, CompanyMatcher.resolve(certificate("ABC SOLRUTIONS INC."), listOf(abc)).suggestedName)
        assertEquals(abc, CompanyMatcher.resolve(certificate("ABC SOLUTINOS INC."), listOf(abc)).suggestedName)
    }
    @Test fun twoTyposInLongNameAreTolerated() {
        val name = "SUMMITBROOK TECHNOLOGY SERVICES INC."
        val result = CompanyMatcher.resolve(certificate("SUMMITBROOK TECHNOL0GY SERVlCES INC."), listOf(name))
        // Digits in a changed identifier are deliberately not silently normalized.
        assertFalse(result.matchedFromList)
        assertEquals(name, CompanyMatcher.resolve(certificate("SUMMITBROOK TECHNOLQGY SERVlCES INC."), listOf(name)).suggestedName)
    }
    @Test fun closeCompetingMatchesLeaveFieldEmpty() {
        val result = CompanyMatcher.resolve(certificate("CEDARSTONE FOOD DISTRIBUTIO CORPORATION"), cedar)
        assertNull(result.suggestedName)
        assertTrue(result.ambiguous)
        assertNotNull(result.evidence)
    }
    @Test fun typoInListRemainsEditableSuggestionNotSilentCorrection() {
        val listName = "WESTBROOK GENERAL MERCHANDISE CORPRATION"
        val result = CompanyMatcher.resolve(certificate("WESTBROOK GENERAL MERCHANDISE CORPORATION"), listOf(listName))
        assertEquals(listName, result.suggestedName)
        assertEquals("WESTBROOK GENERAL MERCHANDISE CORPORATION", result.evidence)
    }
    @Test fun shortAcronymMismatchDoesNotSelectDifferentCompany() {
        val result = CompanyMatcher.resolve(certificate("ABD SOLUTIONS INC."), listOf(abc))
        assertEquals("ABD SOLUTIONS INC.", result.suggestedName)
        assertFalse(result.matchedFromList)
    }
    @Test fun differentLegalEntityTypesAreNotFuzzyMatches() {
        assertFalse(CompanyMatcher.resolve(certificate("ABC SOLUTIONS CORP."), listOf(abc)).matchedFromList)
    }
    @Test fun knownSuffixAbbreviationsCanMatch() {
        assertEquals(abc, CompanyMatcher.resolve(certificate("ABC SOLUTIONS INCORPORATED"), listOf(abc)).suggestedName)
    }
    @Test fun unlistedHolderAndMissingListUseDocumentFallback() {
        val name = "WINDWARD EQUIPMENT CORPORATION"
        assertEquals(name, CompanyMatcher.resolve(certificate(name), listOf(abc)).suggestedName)
        assertEquals(name, CompanyMatcher.resolve(certificate(name), emptyList()).suggestedName)
    }
    @Test fun listedMentionIsNotProofOfHolder() {
        val text = "SECRETARY'S CERTIFICATE\nDO HEREBY CERTIFY THAT:\nRESOLVED, receive payment from ABC SOLUTIONS INC."
        assertNull(CompanyMatcher.resolve(text, listOf(abc)).suggestedName)
    }
    @Test fun listedBankDoesNotOverrideSecretaryHolder() {
        val text = "SECRETARY'S CERTIFICATE\nCorporate Secretary of ABC SOLUTIONS INC. (the Corporation)\nDO HEREBY CERTIFY THAT:\nRESOLVED, contact OTHER SERVICES INC."
        assertEquals(abc, CompanyMatcher.resolve(text, listOf("OTHER SERVICES INC.")).suggestedName)
    }
    @Test fun multipleHoldersRequireChoiceEvenIfOnlyOneIsListed() {
        val result = CompanyMatcher.resolve(certificate(abc) + "\u000c" + certificate("OTHER SERVICES INC."), listOf(abc))
        assertNull(result.suggestedName)
        assertTrue(result.ambiguous)
    }
    @Test fun repeatedSameHolderAcrossPagesIsNotAmbiguous() {
        assertEquals(abc, CompanyMatcher.resolve(certificate(abc) + "\u000c" + certificate(abc), listOf(abc)).suggestedName)
    }
    @Test fun equivalentListRowsDoNotCreateAmbiguity() {
        assertEquals(abc, CompanyMatcher.resolve(certificate(abc), listOf(abc, "abc solutions inc")).suggestedName)
    }
}
