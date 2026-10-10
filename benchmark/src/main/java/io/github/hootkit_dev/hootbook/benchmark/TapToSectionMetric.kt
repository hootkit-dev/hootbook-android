package io.github.hootkit_dev.hootbook.benchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.traceprocessor.TraceProcessor

/**
 * Milliseconds from the last input event the app received before [sectionName] first
 * appears in its trace (the tap's ACTION_UP) to the start of that section.
 *
 * Read from the Perfetto trace, so unlike polling the UI with UiAutomator it adds no work
 * to the measured app and resolves to the frame. Builds that never emit the section (e.g.
 * a baseline from before it existed) report nothing.
 */
@OptIn(ExperimentalMetricApi::class)
class TapToSectionMetric(
    private val sectionName: String,
    private val metricName: String,
) : TraceMetric() {
    override fun getMeasurements(
        captureInfo: CaptureInfo,
        traceSession: TraceProcessor.Session,
    ): List<Measurement> {
        val appThreads = """
            SELECT utid FROM thread JOIN process USING (upid)
            WHERE process.name = '${captureInfo.targetPackageName}'
        """
        val row = traceSession.query(
            """
            WITH section AS (
              SELECT min(s.ts) AS ts FROM slice s JOIN thread_track tt ON s.track_id = tt.id
              WHERE tt.utid IN ($appThreads) AND s.name = '$sectionName'
            ),
            tap AS (
              SELECT max(s.ts) AS ts FROM slice s JOIN thread_track tt ON s.track_id = tt.id
              WHERE tt.utid IN ($appThreads) AND s.name LIKE 'deliverInputEvent%'
                AND s.ts < (SELECT ts FROM section)
            )
            SELECT (SELECT ts FROM section) - (SELECT ts FROM tap) AS dur
            """.trimIndent()
        ).firstOrNull() ?: return emptyList()
        val durNs = row.nullableLong("dur") ?: return emptyList()
        return listOf(Measurement(metricName, durNs / 1_000_000.0))
    }
}
