package io.github.hootkit_dev.hootbook.presentation.core.navigation

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Uses Android Uri instead of a host mock; protects string IDs at the UI navigation boundary. */
class CoreRoutesInstrumentedTest {
    @Test fun contextualTransactionRoutePreservesEveryStringId() {
        val account = "account /?&=中"
        val transaction = "transaction /?&=中"
        val subscription = "subscription /?&=中"
        val template = "template /?&=中"
        val route = Uri.parse("https://fixture.invalid/" +
            coreTransactionsRoute(account, transaction, subscription, template))
        assertEquals(account, route.getQueryParameter("accountId"))
        assertEquals(transaction, route.getQueryParameter("transactionId"))
        assertEquals(subscription, route.getQueryParameter("subscriptionId"))
        assertEquals(template, route.getQueryParameter("templateId"))
    }

    @Test fun rootRouteHasNoStaleContext() {
        assertEquals("transactions", coreTransactionsRoute())
        val route = Uri.parse("https://fixture.invalid/" + coreTransactionsRoute())
        assertNull(route.query)
    }
}
