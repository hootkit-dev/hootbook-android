package io.github.hootkit_dev.hootbook.presentation.ui.features.investments

import androidx.compose.runtime.Composable
import io.github.hootkit_dev.hootbook.R

/**
 * Extensible provider interface for broker integrations.
 * Future brokers (e.g. Charles Schwab, Robinhood, Futu, Tiger Brokers) can implement this interface
 * to register into Hootbook's broker auto-sync system.
 */
interface BrokerProvider {
    /** Unique identifier for this provider (e.g. "ibkr_flex"). */
    val id: String

    /** Localized name string resource of the provider. */
    val nameRes: Int

    /** Localized description / subtitle string resource for this provider. */
    val descriptionRes: Int

    /** Drawable resource for the provider's logo/icon, if statically bundled. */
    val iconResId: Int?

    /** Whether this provider is currently available for connection. */
    val isAvailable: Boolean get() = true

    /**
     * Composable dialog / sheet for gathering user credentials and initiating connection.
     */
    @Composable
    fun ConnectDialog(
        busy: Boolean,
        error: CoreInvestmentFailure?,
        onDismiss: () -> Unit,
        onConnect: (label: String, fields: Map<String, String>) -> Unit,
    )
}

/**
 * Interactive Brokers (IBKR Flex Query) provider implementation.
 */
object IbkrFlexBrokerProvider : BrokerProvider {
    override val id: String = "ibkr_flex"
    override val nameRes: Int = R.string.broker_name_label
    override val descriptionRes: Int = R.string.broker_auto_sync_body
    override val iconResId: Int? = null

    @Composable
    override fun ConnectDialog(
        busy: Boolean,
        error: CoreInvestmentFailure?,
        onDismiss: () -> Unit,
        onConnect: (label: String, fields: Map<String, String>) -> Unit,
    ) {
        IbkrConnectDialog(
            busy = busy,
            error = error,
            onDismiss = onDismiss,
            onConnect = { label, token, query ->
                onConnect(label, mapOf("token" to token.trim(), "queryId" to query.trim()))
            }
        )
    }
}

/**
 * Registry of broker providers, extensible for future brokerage integrations.
 */
object BrokerProviderRegistry {
    private val providers: List<BrokerProvider> = listOf(
        IbkrFlexBrokerProvider,
    )

    fun find(id: String): BrokerProvider? = providers.firstOrNull { it.id == id }

    fun all(): List<BrokerProvider> = providers
}
