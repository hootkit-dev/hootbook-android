package io.github.hootkit_dev.hootbook.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.database.entity.QuickTemplateEntity
import io.github.hootkit_dev.hootbook.data.repository.QuickTemplateRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * Home-screen widget: "+" for an empty Add form, and the first quick templates, each opening
 * the form filled in with the number pad up for the amount. [QuickEntryPublisher] refreshes
 * it whenever the templates change.
 */
class QuickAddWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun quickTemplateRepository(): QuickTemplateRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val templates = try {
            EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
                .quickTemplateRepository().templates.first().take(QuickEntry.MAX_TEMPLATES)
        } catch (e: Exception) {
            Log.w("QuickAddWidget", "Templates not loaded", e)
            emptyList()
        }
        provideContent {
            GlanceTheme { Content(context, templates) }
        }
    }

    @Composable
    private fun Content(context: Context, templates: List<QuickTemplateEntity>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(12.dp)
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = context.getString(R.string.widget_quick_add_title),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = GlanceModifier.defaultWeight().padding(start = 4.dp)
                )
                Box(
                    modifier = GlanceModifier
                        .size(36.dp)
                        .cornerRadius(18.dp)
                        .background(GlanceTheme.colors.primary)
                        .clickable(actionStartActivity(QuickEntry.addIntent(context))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 22.sp)
                    )
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            if (templates.isEmpty()) {
                Text(
                    text = context.getString(R.string.widget_quick_add_empty),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    maxLines = 3,
                    modifier = GlanceModifier.padding(horizontal = 4.dp)
                )
            } else {
                templates.chunked(2).forEachIndexed { index, row ->
                    if (index > 0) Spacer(GlanceModifier.height(8.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        TemplateChip(context, row[0], GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(8.dp))
                        if (row.size > 1) {
                            TemplateChip(context, row[1], GlanceModifier.defaultWeight())
                        } else {
                            Spacer(GlanceModifier.defaultWeight())
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TemplateChip(context: Context, template: QuickTemplateEntity, modifier: GlanceModifier) {
        Box(
            modifier = modifier
                .height(40.dp)
                .cornerRadius(12.dp)
                .background(GlanceTheme.colors.secondaryContainer)
                .clickable(actionStartActivity(QuickEntry.templateIntent(context, template.id)))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = QuickEntry.label(template),
                style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 13.sp),
                maxLines = 1
            )
        }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}
