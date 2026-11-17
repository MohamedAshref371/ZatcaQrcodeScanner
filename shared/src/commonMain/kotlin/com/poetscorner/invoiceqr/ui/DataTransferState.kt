package com.poetscorner.invoiceqr.ui

enum class TransferKind { EXPORT, BACKUP, IMPORT }
data class DataTransferState(val kind: TransferKind, val processed: Int = 0, val total: Int? = null,
    val saving: Boolean = false)
