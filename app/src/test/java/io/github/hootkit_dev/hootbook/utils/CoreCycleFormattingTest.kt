package io.github.hootkit_dev.hootbook.utils

import dev.hootkit.core.Cycle
import dev.hootkit.core.CycleUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreCycleFormattingTest {
    @Test fun generatedUnitsHaveSingularDisplayNames() { for ((unit,label) in listOf(CycleUnit.DAY to "day", CycleUnit.WEEK to "week", CycleUnit.MONTH to "month", CycleUnit.YEAR to "year")) assertEquals("Every $label",formatCoreCycle(Cycle(1u,unit))) }
    @Test fun generatedCountIsDisplayedWithoutRecalculatingPeriods() { assertEquals("Every 3 months",formatCoreCycle(Cycle(3u,CycleUnit.MONTH))) }
    @Test fun unknownUnitStaysUnknownInsteadOfInventingMonthlyBehavior() { assertEquals("Unknown cycle",formatCoreCycle(Cycle(1u,CycleUnit.UNKNOWN))) }
}
