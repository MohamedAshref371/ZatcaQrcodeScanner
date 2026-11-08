package com.poetscorner.invoiceqr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TemporaryInvoiceFilesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun removesOnlyAbandonedInvoiceFilesInCacheRoot() {
        val abandoned = listOf(
            "invoiceqr-import-123.pdf",
            "history-import-123.json",
            "history-export-123.json"
        ).map(folder::newFile)
        val unrelated = listOf(
            "history-export-123.csv",
            "history-import-123.txt",
            "unrelated.json"
        ).map(folder::newFile)
        val directory = folder.newFolder("history-import-folder.json")
        val nested = folder.newFolder("nested").resolve("history-export-456.json").apply { writeText("kept") }

        TemporaryInvoiceFiles.cleanAbandoned(folder.root)

        abandoned.forEach { assertFalse(it.name, it.exists()) }
        unrelated.forEach { assertTrue(it.name, it.exists()) }
        assertTrue(directory.exists())
        assertTrue(nested.exists())
    }
}
