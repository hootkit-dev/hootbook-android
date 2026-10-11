package io.github.hootkit_dev.hootbook.presentation.core.navigation

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CoreNavigationRoutesTest {
    @Test fun currencyWithoutSelectionUsesManagementRoot() {
        assertEquals("currencies", coreCurrenciesRoute())
    }

    @Test fun currencyParameterCannotCreateAnotherQueryArgument() {
        val code = "X /?&currency=JPY#中文"
        val uri = Uri.parse(coreCurrenciesRoute(code))
        assertEquals("currencies", uri.path)
        assertEquals(setOf("currency"), uri.queryParameterNames)
        assertEquals(code, uri.getQueryParameter("currency"))
    }

    @Test fun transactionIdentifiersRemainStringsAndAreIndividuallyEncoded() {
        val ids = listOf("001 /?&x=1", "transaction#片", "subscription+1", "template=2")
        val uri = Uri.parse(coreTransactionsRoute(ids[0], ids[1], ids[2], ids[3]))
        assertEquals("transactions", uri.path)
        assertEquals(setOf("accountId", "transactionId", "subscriptionId", "templateId"), uri.queryParameterNames)
        listOf("accountId", "transactionId", "subscriptionId", "templateId").forEachIndexed { i, name ->
            assertEquals(ids[i], uri.getQueryParameter(name))
        }
    }
}
