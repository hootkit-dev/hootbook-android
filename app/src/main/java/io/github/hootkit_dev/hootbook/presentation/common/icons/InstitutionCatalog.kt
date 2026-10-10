package io.github.hootkit_dev.hootbook.presentation.common.icons

import io.github.hootkit_dev.hootbook.R
import java.util.Locale

enum class InstitutionKind { BANK, BROKER }

/** Curated account identities. Stable IDs and drawable names are safe to persist in backups. */
data class Institution(
    val id: String,
    val chineseName: String,
    val englishName: String,
    val region: String,
    val currency: String,
    val iconResId: Int,
    val color: String,
    val aliases: List<String>,
    val kind: InstitutionKind = InstitutionKind.BANK
) {
    val iconName: String get() = "ic_institution_$id"
    /** Accounts named after a broker are investment accounts (`AccountBalanceEntity.category()`). */
    val isBroker: Boolean get() = kind == InstitutionKind.BROKER
    fun displayName(language: String): String = if (language == "zh") chineseName else englishName
    val searchTerms: List<String> get() = listOf(chineseName, englishName) + aliases
}

object InstitutionCatalog {
    val institutions: List<Institution> = listOf(
        Institution("boc", "中国银行", "Bank of China", "CN", "CNY", R.drawable.ic_institution_boc, "#B51C31", listOf("BOC", "中行", "中國銀行")),
        Institution("ccb", "中国建设银行", "China Construction Bank", "CN", "CNY", R.drawable.ic_institution_ccb, "#005BAC", listOf("CCB", "建设银行", "建行", "中國建設銀行")),
        Institution("cmb", "招商银行", "China Merchants Bank", "CN", "CNY", R.drawable.ic_institution_cmb, "#C41230", listOf("CMB", "招行", "招商銀行")),
        Institution("icbc", "中国工商银行", "ICBC", "CN", "CNY", R.drawable.ic_institution_icbc, "#C7000B", listOf("工商银行", "工行", "中國工商銀行")),
        Institution("abc", "中国农业银行", "Agricultural Bank of China", "CN", "CNY", R.drawable.ic_institution_abc, "#008566", listOf("ABC", "农业银行", "农行", "中國農業銀行")),
        Institution("bocom", "交通银行", "Bank of Communications", "CN", "CNY", R.drawable.ic_institution_bocom, "#003B90", listOf("BOCOM", "交行", "交通銀行")),
        Institution("psbc", "中国邮政储蓄银行", "Postal Savings Bank of China", "CN", "CNY", R.drawable.ic_institution_psbc, "#007D42", listOf("PSBC", "邮储银行", "邮储", "中國郵政儲蓄銀行")),
        Institution("citic_cn", "中信银行", "China CITIC Bank", "CN", "CNY", R.drawable.ic_institution_citic_cn, "#C71724", listOf("CITIC", "中信銀行")),
        Institution("spdb", "浦发银行", "Shanghai Pudong Development Bank", "CN", "CNY", R.drawable.ic_institution_spdb, "#003B78", listOf("SPDB", "浦发", "浦發銀行")),
        Institution("cib", "兴业银行", "Industrial Bank", "CN", "CNY", R.drawable.ic_institution_cib, "#005BAC", listOf("CIB", "兴业", "興業銀行")),
        Institution("cmbc", "中国民生银行", "China Minsheng Bank", "CN", "CNY", R.drawable.ic_institution_cmbc, "#006F68", listOf("CMBC", "民生银行", "民生", "中國民生銀行")),
        Institution("ceb", "中国光大银行", "China Everbright Bank", "CN", "CNY", R.drawable.ic_institution_ceb, "#6A1685", listOf("CEB", "光大银行", "光大", "中國光大銀行")),
        Institution("pingan", "平安银行", "Ping An Bank", "CN", "CNY", R.drawable.ic_institution_pingan, "#F47920", listOf("Ping An", "平安銀行")),
        Institution("hxb", "华夏银行", "Huaxia Bank", "CN", "CNY", R.drawable.ic_institution_hxb, "#C91825", listOf("HXB", "华夏", "華夏銀行")),
        Institution("cgb", "广发银行", "China Guangfa Bank", "CN", "CNY", R.drawable.ic_institution_cgb, "#C81528", listOf("CGB", "广发", "廣發銀行")),
        Institution("hsbc_hk", "汇丰香港", "HSBC Hong Kong", "HK", "HKD", R.drawable.ic_institution_hsbc_hk, "#DB0011", listOf("HSBC HK", "汇丰银行香港", "香港汇丰", "香港滙豐", "滙豐香港")),
        Institution("bochk", "中银香港", "Bank of China Hong Kong", "HK", "HKD", R.drawable.ic_institution_bochk, "#B51C31", listOf("BOC HK", "BOCHK", "中国银行香港", "中國銀行香港", "中銀香港")),
        Institution("hangseng", "恒生银行", "Hang Seng Bank", "HK", "HKD", R.drawable.ic_institution_hangseng, "#008244", listOf("Hang Seng", "恒生銀行")),
        Institution("ocbc_sg", "华侨银行（新加坡）", "OCBC Singapore", "SG", "SGD", R.drawable.ic_institution_ocbc_sg, "#D71920", listOf("OCBC SG", "OCBC", "华侨银行", "華僑銀行")),
        Institution("dbs", "星展银行（新加坡）", "DBS Singapore", "SG", "SGD", R.drawable.ic_institution_dbs, "#E60028", listOf("DBS SG", "DBS", "星展银行", "星展銀行")),
        Institution("uob", "大华银行（新加坡）", "UOB Singapore", "SG", "SGD", R.drawable.ic_institution_uob, "#003B71", listOf("UOB SG", "UOB", "大华银行", "大華銀行")),
        Institution("ibkr", "盈透证券", "Interactive Brokers", "US", "USD", R.drawable.ic_institution_ibkr, "#D71920", listOf("IBKR", "盈透", "盈透證券"), InstitutionKind.BROKER),
        Institution("chase", "大通银行", "Chase", "US", "USD", R.drawable.ic_institution_chase, "#117ACA", listOf("JPMorgan Chase", "JP Morgan", "大通銀行")),
        Institution("bofa", "美国银行", "Bank of America", "US", "USD", R.drawable.ic_institution_bofa, "#E31837", listOf("BofA", "BOA", "美國銀行")),
        Institution("sofi", "SoFi", "SoFi", "US", "USD", R.drawable.ic_institution_sofi, "#00A5AA", listOf("Social Finance")),
        Institution("schwab", "嘉信理财", "Charles Schwab", "US", "USD", R.drawable.ic_institution_schwab, "#00A0DF", listOf("Schwab", "嘉信", "嘉信理財"), InstitutionKind.BROKER),
        Institution("capital_one", "第一资本", "Capital One", "US", "USD", R.drawable.ic_institution_capital_one, "#004977", listOf("CapitalOne", "第一資本")),
        Institution("amex_us", "美国运通（美国）", "American Express US", "US", "USD", R.drawable.ic_institution_amex_us, "#006FCF", listOf("AMEX US", "AMEX", "American Express", "美国运通", "美國運通")),
        Institution("citi_us", "花旗银行（美国）", "Citibank US", "US", "USD", R.drawable.ic_institution_citi_us, "#056DAE", listOf("Citi US", "Citibank", "Citi", "花旗银行", "花旗銀行")),
        Institution("wells_fargo", "富国银行", "Wells Fargo", "US", "USD", R.drawable.ic_institution_wells_fargo, "#D71E28", listOf("富國銀行")),
        Institution("fidelity", "富达投资", "Fidelity Investments", "US", "USD", R.drawable.ic_institution_fidelity, "#007A3E", listOf("Fidelity", "富達投資"), InstitutionKind.BROKER),
        Institution("vanguard", "先锋领航", "Vanguard", "US", "USD", R.drawable.ic_institution_vanguard, "#96151D", listOf("先鋒領航"), InstitutionKind.BROKER),
        Institution("robinhood", "Robinhood", "Robinhood", "US", "USD", R.drawable.ic_institution_robinhood, "#00C805", listOf("罗宾汉", "羅賓漢"), InstitutionKind.BROKER),
        Institution("futu", "富途证券", "Futu", "HK", "HKD", R.drawable.ic_institution_futu, "#FF6900", listOf("Futu", "富途", "富途證券", "富途牛牛", "Futubull"), InstitutionKind.BROKER),
        Institution("moomoo", "moomoo", "moomoo", "US", "USD", R.drawable.ic_institution_moomoo, "#FF6A00", listOf("Moomoo SG", "Moomoo US"), InstitutionKind.BROKER),
        Institution("tiger", "老虎证券", "Tiger Brokers", "SG", "USD", R.drawable.ic_institution_tiger, "#FFD200", listOf("Tiger Trade", "老虎證券"), InstitutionKind.BROKER),
        Institution("longbridge", "长桥证券", "Longbridge", "HK", "HKD", R.drawable.ic_institution_longbridge, "#00B8B8", listOf("Longbridge Securities", "LongPort", "长桥", "長橋", "長橋證券"), InstitutionKind.BROKER),
        Institution("webull", "微牛证券", "Webull", "US", "USD", R.drawable.ic_institution_webull, "#1A5AF5", listOf("微牛", "微牛證券"), InstitutionKind.BROKER),
        Institution("snowball", "雪盈证券", "Snowball X", "HK", "USD", R.drawable.ic_institution_snowball, "#1F8FFF", listOf("Snowball Securities", "雪盈", "雪盈證券"), InstitutionKind.BROKER),
        Institution("usmart", "盈立证券", "uSMART", "HK", "HKD", R.drawable.ic_institution_usmart, "#1E6BFF", listOf("uSmart Securities", "盈立", "盈立證券"), InstitutionKind.BROKER),
        Institution("vbrokers", "华盛通", "VBrokers", "HK", "HKD", R.drawable.ic_institution_vbrokers, "#E60012", listOf("Valuable Capital", "华盛证券", "華盛通", "華盛證券"), InstitutionKind.BROKER),
        Institution("bright_smart", "耀才证券", "Bright Smart Securities", "HK", "HKD", R.drawable.ic_institution_bright_smart, "#F08300", listOf("Bright Smart", "耀才", "耀才證券"), InstitutionKind.BROKER),
        Institution("eastmoney", "东方财富证券", "East Money Securities", "CN", "CNY", R.drawable.ic_institution_eastmoney, "#FF5000", listOf("East Money", "Eastmoney", "东方财富", "東方財富", "東方財富證券", "东财"), InstitutionKind.BROKER),
        Institution("huatai", "华泰证券", "Huatai Securities", "CN", "CNY", R.drawable.ic_institution_huatai, "#E60012", listOf("HTSC", "華泰證券", "涨乐财富通"), InstitutionKind.BROKER),
        Institution("citic_sec", "中信证券", "CITIC Securities", "CN", "CNY", R.drawable.ic_institution_citic_sec, "#C7000B", listOf("中信證券", "信e投"), InstitutionKind.BROKER),
        Institution("cms_sec", "招商证券", "China Merchants Securities", "CN", "CNY", R.drawable.ic_institution_cms_sec, "#C8102E", listOf("招商證券"), InstitutionKind.BROKER),
        Institution("gtja", "国泰海通证券", "Guotai Haitong Securities", "CN", "CNY", R.drawable.ic_institution_gtja, "#E60012", listOf("Guotai Junan", "GTJA", "国泰君安", "國泰君安", "国泰海通", "國泰海通", "君弘"), InstitutionKind.BROKER),
    )

    private val institutionsById = institutions.associateBy { it.id }

    fun byId(id: String): Institution? = institutionsById[id]

    /** True when an account with this name counts as an investment account. */
    fun isBrokerName(name: String): Boolean = find(name)?.isBroker == true

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() }

    private data class MatchTerm(val text: String, val institution: Institution) {
        val normalized = normalize(text)
        val pattern = if (text.any { it.code > 127 }) null else
            Regex("(?i)(?<![a-z0-9])" + Regex.escape(text) + "(?![a-z0-9])")
        fun matches(name: String): Boolean = pattern?.containsMatchIn(name)
            ?: name.contains(text, ignoreCase = true)
    }
    private val matchTerms = institutions.flatMap { institution ->
        institution.searchTerms.map { MatchTerm(it, institution) }
    }.sortedByDescending { it.text.length }

    /** Searches Chinese names, English names, abbreviations, region, and currency. */
    fun search(query: String, region: String? = null, brokersOnly: Boolean = false): List<Institution> {
        val terms = query.trim().split(Regex("\\s+")).map(::normalize).filter(String::isNotEmpty)
        return institutions.filter { institution ->
            (region == null || institution.region == region) && (!brokersOnly || institution.isBroker) && terms.all { term ->
                (institution.searchTerms + institution.region + institution.currency)
                    .any { normalize(it).contains(term) }
            }
        }
    }

    /** Prefer exact and longest names, so BOC HK is never mistaken for mainland BOC. */
    fun find(name: String): Institution? {
        val normalized = normalize(name)
        if (normalized.isEmpty()) return null
        matchTerms.firstOrNull { it.normalized == normalized }?.let { return it.institution }
        return matchTerms.firstOrNull { it.matches(name) }?.institution
    }
}
