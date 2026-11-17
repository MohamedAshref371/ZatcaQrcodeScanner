package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.data.HistoryFilter
import com.poetscorner.invoiceqr.data.StatisticsKey
import com.poetscorner.invoiceqr.data.StatisticsRequest
import com.poetscorner.invoiceqr.data.StatisticsSelection
import java.io.ObjectStreamClass
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedStateCompatibilityTest {
    @Test fun migratedTypesRetainTheirStageOneSavedStateIdentifiers() {
        val stageOneIdentifiers = mapOf(
            HistoryFilter::class.java to 8326515410588034567L,
            StatisticsRequest::class.java to 3314063135706167752L,
            StatisticsKey::class.java to -7878493686484506581L,
            StatisticsSelection::class.java to 4886106675832219751L,
        )
        for ((type, serialVersion) in stageOneIdentifiers) {
            assertEquals(type.name, serialVersion, ObjectStreamClass.lookup(type).serialVersionUID)
        }
    }
}
