package com.poetscorner.invoiceqr

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poetscorner.invoiceqr.data.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class StatisticsFilterRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "statistics_filters_test"
    private val preferences get() = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    @Before fun clear() { assertTrue(preferences.edit().clear().commit()) }
    @After fun cleanup() {
        assertTrue(preferences.edit().clear().commit())
        assertTrue(context.deleteSharedPreferences(name))
    }

    @Test fun savesEveryFilterAndRestoresRelativeIntentInANewRepository() {
        val request = StatisticsRequest(seller = "Market", vat = "300", excludedVat = "٣٠٠٠٠٠٠٠٠٠٠٠٠٠١")
            .withPeriod(StatisticsPreset.LAST_QUARTER, LocalDate.of(2026, 12, 20))
        StatisticsFilterRepository(preferences).save(request)
        assertTrue(StatisticsFilterRepository(preferences).hasSavedFilters)
        assertTrue(preferences.edit().commit())
        val loaded = StatisticsFilterRepository(preferences).load(LocalDate.of(2027, 1, 10))
        assertEquals("Market", loaded.seller)
        assertEquals("", loaded.vat)
        assertEquals(StatisticsVatMode.EXCLUDE, loaded.vatMode)
        assertEquals("300000000000001", loaded.excludedVat)
        assertEquals(StatisticsPreset.LAST_QUARTER, loaded.datePreset)
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay(), loaded.fromDay)
        assertEquals(LocalDate.of(2026, 12, 31).toEpochDay(), loaded.toDay)
    }

    @Test fun manualDatesRemainFixedAndClearingFiltersPersists() {
        val request = StatisticsRequest(seller = "Market", excludedVat = "300000000000001",
            fromDay = LocalDate.of(2026, 10, 1).toEpochDay(), toDay = LocalDate.of(2026, 10, 31).toEpochDay())
        StatisticsFilterRepository(preferences).save(request)
        assertEquals(request, StatisticsFilterRepository(preferences).load(LocalDate.of(2027, 1, 1)))
        StatisticsFilterRepository(preferences).save(StatisticsRequest())
        assertEquals(StatisticsRequest(), StatisticsFilterRepository(preferences).load())
    }

    @Test fun remembersTheModeEvenWithAnEmptyNumberAndMigratesOlderExclusionFilters() {
        val repository = StatisticsFilterRepository(preferences)
        repository.save(StatisticsRequest().withVatFilter(StatisticsVatMode.EXCLUDE, ""))
        assertEquals(StatisticsVatMode.EXCLUDE, StatisticsFilterRepository(preferences).load().vatMode)
        preferences.edit().clear().putString("vat", "300").putString("excluded_vat", "300000000000001").commit()
        val migrated = StatisticsFilterRepository(preferences).load()
        assertEquals(StatisticsVatMode.EXCLUDE, migrated.vatMode)
        assertEquals("300000000000001", migrated.excludedVat)
        assertEquals("", migrated.vat)
    }

    @Test fun savesCategoryCalculationsAndRestoresDefaultsWhenCleared() {
        val repository = StatisticsFilterRepository(preferences)
        val request = StatisticsRequest()
            .withCategoryCalculation(0, StatisticsCategoryCalculation.IGNORE)
            .withCategoryCalculation(2, StatisticsCategoryCalculation.SUBTRACT)
        repository.save(request)
        assertEquals(request.categoryCalculations, StatisticsFilterRepository(preferences).load().categoryCalculations)
        repository.save(StatisticsRequest())
        assertTrue(StatisticsFilterRepository(preferences).load().categoryCalculations.isEmpty())
    }
}
