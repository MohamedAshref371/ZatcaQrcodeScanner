package com.poetscorner.invoiceqr

import java.io.File

/** A process restart means no previous import or export still owns these cache files. */
internal object TemporaryInvoiceFiles {
    private val names = Regex("(?:invoiceqr-import-[^/]+\\.pdf|history-import-[^/]+\\.json|history-export-[^/]+\\.json)")

    fun cleanAbandoned(cacheDir: File) {
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && names.matches(file.name)) file.delete()
        }
    }
}
