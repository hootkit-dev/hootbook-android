package io.github.hootkit_dev.hootbook.presentation.common.icons

import io.github.hootkit_dev.hootbook.R
import org.junit.Assert.*
import org.junit.Test

class InstitutionCatalogTest {
    @Test fun `CMB icon update preserves aliases and the stored resource name`() {
        listOf("招商银行", "招商銀行", "招行", "CMB", "China Merchants Bank").forEach { name ->
            val institution = requireNotNull(InstitutionCatalog.find(name))
            assertEquals("cmb", institution.id)
            assertEquals(R.drawable.ic_institution_cmb, institution.iconResId)
            assertEquals("ic_institution_cmb", institution.iconName)
        }
    }

    @Test fun `requested institutions resolve by familiar names`() {
        mapOf(
            "IBKR" to "ibkr", "Chase" to "chase", "BofA" to "bofa",
            "SoFi" to "sofi", "Schwab" to "schwab", "Capital One" to "capital_one",
            "AMEX US" to "amex_us", "BOC" to "boc", "OCBC (SG)" to "ocbc_sg",
            "HSBC HK" to "hsbc_hk", "BOC HK" to "bochk",
            "中国建设银行" to "ccb", "招商银行" to "cmb"
        ).forEach { (name, id) -> assertEquals(name, id, InstitutionCatalog.find(name)?.id) }
    }

    @Test fun `hong kong and mainland identities stay distinct`() {
        assertEquals("HKD", InstitutionCatalog.find("BOC HK savings")?.currency)
        assertEquals("CNY", InstitutionCatalog.find("BOC savings")?.currency)
        assertEquals("bochk", InstitutionCatalog.find("中國銀行香港")?.id)
        assertEquals("citic_cn", InstitutionCatalog.find("China CITIC Bank")?.id)
        assertEquals("citi_us", InstitutionCatalog.find("Citibank US")?.id)
    }

    @Test fun `search supports Chinese abbreviations and multiple words`() {
        assertEquals("ccb", InstitutionCatalog.search("建行").single().id)
        assertEquals("bofa", InstitutionCatalog.search("bank america").single().id)
        assertEquals("ibkr", InstitutionCatalog.search("IBKR US").single().id)
        assertTrue(InstitutionCatalog.search("boc", "US").isEmpty())
        // Singapore's banks hold SGD; a broker there (Tiger) defaults to USD
        assertTrue(InstitutionCatalog.search("", "SG").filterNot { it.isBroker }.all { it.currency == "SGD" })
    }

    @Test fun `short aliases do not match unrelated merchants`() {
        listOf("boat shop", "abcde", "chased delivery", "citizen", "", "   ")
            .forEach { assertNull(it, InstitutionCatalog.find(it)) }
    }

    @Test fun `persisted identity names and IDs are unique`() {
        val institutions = InstitutionCatalog.institutions
        assertEquals(institutions.size, institutions.map { it.id }.distinct().size)
        assertEquals(institutions.size, institutions.map { it.iconName }.distinct().size)
    }

    @Test fun `brokers are marked on their entry and found by familiar names`() {
        mapOf(
            "IBKR" to "ibkr", "富途证券" to "futu", "moomoo" to "moomoo", "老虎证券" to "tiger",
            "Tiger Trade" to "tiger", "长桥证券" to "longbridge", "Longbridge" to "longbridge",
            "Webull" to "webull", "微牛" to "webull", "东方财富证券" to "eastmoney", "华泰证券" to "huatai",
            "中信证券" to "citic_sec", "CITIC Securities" to "citic_sec", "招商证券" to "cms_sec",
            "国泰君安" to "gtja", "国泰海通证券" to "gtja", "雪盈证券" to "snowball", "耀才证券" to "bright_smart",
            "盈立证券" to "usmart", "华盛通" to "vbrokers", "富途 美股" to "futu"
        ).forEach { (name, id) ->
            val institution = InstitutionCatalog.find(name)
            assertEquals(name, id, institution?.id)
            assertTrue(name, institution!!.isBroker)
            assertTrue(name, InstitutionCatalog.isBrokerName(name))
        }
        // The original six keep their ids and stay brokers
        listOf("ibkr", "schwab", "fidelity", "vanguard", "robinhood", "futu")
            .forEach { assertTrue(it, InstitutionCatalog.byId(it)!!.isBroker) }
    }

    @Test fun `securities arms do not take their banks' names`() {
        assertEquals("cmb", InstitutionCatalog.find("招商银行")?.id)
        assertEquals("citic_cn", InstitutionCatalog.find("中信银行")?.id)
        assertEquals("citic_cn", InstitutionCatalog.find("China CITIC Bank")?.id)
        listOf("招商银行", "中信银行", "华泰保险", "老虎堂", "Tiger Sugar", "Chase", "")
            .forEach { assertFalse(it, InstitutionCatalog.isBrokerName(it)) }
    }

    @Test fun `broker search lists only brokers`() {
        val brokers = InstitutionCatalog.search("", brokersOnly = true)
        assertTrue(brokers.isNotEmpty())
        assertTrue(brokers.all { it.isBroker })
        assertEquals(InstitutionCatalog.institutions.count { it.isBroker }, brokers.size)
        assertTrue(InstitutionCatalog.search("招商", brokersOnly = true).all { it.id == "cms_sec" })
    }
}
