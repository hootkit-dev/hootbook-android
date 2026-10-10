package io.github.hootkit_dev.hootbook.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.net.toUri
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.glance.appwidget.updateAll
import io.github.hootkit_dev.hootbook.MainActivity
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.database.entity.QuickTemplateEntity
import io.github.hootkit_dev.hootbook.data.repository.QuickTemplateRepository
import io.github.hootkit_dev.hootbook.di.ApplicationScope
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Ways into the Add form with a quick template already filled in. */
object QuickEntry {
    /** Templates shown on the widget, in the user's order. */
    const val MAX_TEMPLATES = 4

    /** Templates offered as launcher shortcuts: launchers show about four, one being "Add". */
    const val MAX_SHORTCUT_TEMPLATES = 3

    private const val SHORTCUT_PREFIX = "template_"

    /** Opens the Add form filled from [templateId]. The data URI keeps each template's intent distinct. */
    fun templateIntent(context: Context, templateId: Long): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_ADD_TRANSACTION)
            .setData("hootbook://template/$templateId".toUri())
            .putExtra(MainActivity.EXTRA_TEMPLATE_ID, templateId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** Opens an empty Add form. */
    fun addIntent(context: Context): Intent = actionIntent(context, MainActivity.ACTION_ADD_TRANSACTION)

    fun actionIntent(context: Context, action: String): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** "Metro · ¥4.00" when the template fills the amount, else its name. */
    fun label(template: QuickTemplateEntity): String {
        val amount = template.amount?.takeIf { template.prefillAmount } ?: return template.name
        return template.name + " · " + CurrencyFormatter.formatCurrency(amount, template.currency ?: "CNY")
    }

    fun shortcutId(templateId: Long) = "$SHORTCUT_PREFIX$templateId"

    fun isTemplateShortcut(id: String) = id.startsWith(SHORTCUT_PREFIX)
}

/**
 * Keeps the launcher shortcuts and the home-screen widget in step with the quick templates:
 * the first [QuickEntry.MAX_TEMPLATES] become dynamic shortcuts, and pinned shortcuts of
 * deleted templates are disabled.
 */
@Singleton
class QuickEntryPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val templates: QuickTemplateRepository,
    @ApplicationScope private val scope: CoroutineScope
) {
    fun start() {
        scope.launch {
            templates.templates.distinctUntilChanged().collect { all ->
                publishShortcuts(all.take(QuickEntry.MAX_SHORTCUT_TEMPLATES), all)
                try {
                    QuickAddWidget().updateAll(context)
                } catch (e: Exception) {
                    Log.w(TAG, "Widget not updated", e)
                }
            }
        }
    }

    private fun publishShortcuts(shown: List<QuickTemplateEntity>, all: List<QuickTemplateEntity>) {
        try {
            // Templates first, then transfer and subscription for whatever room is left
            val dynamic = shown.mapIndexed { rank, t -> shortcut(t, rank) } + listOf(
                actionShortcut("dyn_add_transfer", R.string.add_transfer, R.drawable.ic_shortcut_transfer,
                    MainActivity.ACTION_ADD_TRANSFER, shown.size),
                actionShortcut("dyn_add_subscription", R.string.add_subscription, R.drawable.ic_shortcut_add_subscription,
                    MainActivity.ACTION_ADD_SUBSCRIPTION, shown.size + 1)
            )
            // Launchers rate-limit updates from apps in the background (a widget update starts
            // the process too), so only publish when something shown changed.
            fun ShortcutInfoCompat.shown() = Triple(id, shortLabel.toString(), longLabel?.toString())
            val current = ShortcutManagerCompat.getDynamicShortcuts(context).sortedBy { it.rank }.map { it.shown() }
            if (current != dynamic.map { it.shown() }) {
                ShortcutManagerCompat.setDynamicShortcuts(context, dynamic)
            }

            // A shortcut pinned to the home screen outlives the dynamic list: keep it current
            // while its template exists, and disable it once the template is deleted.
            val byId = all.associateBy { QuickEntry.shortcutId(it.id) }
            val pinned = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
                .map { it.id }
                .filter { QuickEntry.isTemplateShortcut(it) }
            val (live, gone) = pinned.partition { it in byId }
            if (live.isNotEmpty()) {
                val infos = live.map { shortcut(byId.getValue(it), 0) }
                ShortcutManagerCompat.enableShortcuts(context, infos)
                ShortcutManagerCompat.updateShortcuts(context, infos)
            }
            if (gone.isNotEmpty()) {
                ShortcutManagerCompat.disableShortcuts(context, gone, context.getString(R.string.quick_template_deleted))
            }
        } catch (e: Exception) {
            // Launchers rate-limit shortcut updates; the next template change publishes again.
            Log.w(TAG, "Template shortcuts not published", e)
        }
    }

    private fun actionShortcut(id: String, label: Int, icon: Int, action: String, rank: Int): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(context.getString(label))
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(QuickEntry.actionIntent(context, action))
            .setRank(rank)
            .build()

    private fun shortcut(template: QuickTemplateEntity, rank: Int): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, QuickEntry.shortcutId(template.id))
            .setShortLabel(template.name)
            .setLongLabel(QuickEntry.label(template))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_add_transaction))
            .setIntent(QuickEntry.templateIntent(context, template.id))
            .setRank(rank)
            .build()

    private companion object {
        const val TAG = "QuickEntryPublisher"
    }
}
