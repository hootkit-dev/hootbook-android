package io.github.hootkit_dev.hootbook.presentation.common.icons

import io.github.hootkit_dev.hootbook.R

/** Pure logo artwork only. No account kind, currency, region, balances or ledger identification. */
object InstitutionArtwork {
    data class Artwork(val searchTerms: List<String>, val iconResId: Int, val color: String)
    val items = listOf(
        Artwork(listOf("中国银行", "Bank of China", "BOC", "中行", "中國銀行"), R.drawable.ic_institution_boc, "#B51C31"),
        Artwork(listOf("中国建设银行", "China Construction Bank", "CCB", "建设银行", "建行", "中國建設銀行"), R.drawable.ic_institution_ccb, "#005BAC"),
        Artwork(listOf("招商银行", "China Merchants Bank", "CMB", "招行", "招商銀行"), R.drawable.ic_institution_cmb, "#C41230"),
        Artwork(listOf("中国工商银行", "ICBC", "工商银行", "工行", "中國工商銀行"), R.drawable.ic_institution_icbc, "#C7000B"),
        Artwork(listOf("中国农业银行", "Agricultural Bank of China", "ABC", "农业银行", "农行", "中國農業銀行"), R.drawable.ic_institution_abc, "#008566"),
        Artwork(listOf("交通银行", "Bank of Communications", "BOCOM", "交行", "交通銀行"), R.drawable.ic_institution_bocom, "#003B90"),
        Artwork(listOf("中国邮政储蓄银行", "Postal Savings Bank of China", "PSBC", "邮储银行", "邮储", "中國郵政儲蓄銀行"), R.drawable.ic_institution_psbc, "#007D42"),
        Artwork(listOf("中信银行", "China CITIC Bank", "CITIC", "中信銀行"), R.drawable.ic_institution_citic_cn, "#C71724"),
        Artwork(listOf("浦发银行", "Shanghai Pudong Development Bank", "SPDB", "浦发", "浦發銀行"), R.drawable.ic_institution_spdb, "#003B78"),
        Artwork(listOf("兴业银行", "Industrial Bank", "CIB", "兴业", "興業銀行"), R.drawable.ic_institution_cib, "#005BAC"),
        Artwork(listOf("中国民生银行", "China Minsheng Bank", "CMBC", "民生银行", "民生", "中國民生銀行"), R.drawable.ic_institution_cmbc, "#006F68"),
        Artwork(listOf("中国光大银行", "China Everbright Bank", "CEB", "光大银行", "光大", "中國光大銀行"), R.drawable.ic_institution_ceb, "#6A1685"),
        Artwork(listOf("平安银行", "Ping An Bank", "Ping An", "平安銀行"), R.drawable.ic_institution_pingan, "#F47920"),
        Artwork(listOf("华夏银行", "Huaxia Bank", "HXB", "华夏", "華夏銀行"), R.drawable.ic_institution_hxb, "#C91825"),
        Artwork(listOf("广发银行", "China Guangfa Bank", "CGB", "广发", "廣發銀行"), R.drawable.ic_institution_cgb, "#C81528"),
        Artwork(listOf("汇丰香港", "HSBC Hong Kong", "HSBC HK", "汇丰银行香港", "香港汇丰", "香港滙豐", "滙豐香港"), R.drawable.ic_institution_hsbc_hk, "#DB0011"),
        Artwork(listOf("中银香港", "Bank of China Hong Kong", "BOC HK", "BOCHK", "中国银行香港", "中國銀行香港", "中銀香港"), R.drawable.ic_institution_bochk, "#B51C31"),
        Artwork(listOf("恒生银行", "Hang Seng Bank", "Hang Seng", "恒生銀行"), R.drawable.ic_institution_hangseng, "#008244"),
        Artwork(listOf("华侨银行（新加坡）", "OCBC Singapore", "OCBC SG", "OCBC", "华侨银行", "華僑銀行"), R.drawable.ic_institution_ocbc_sg, "#D71920"),
        Artwork(listOf("星展银行（新加坡）", "DBS Singapore", "DBS SG", "DBS", "星展银行", "星展銀行"), R.drawable.ic_institution_dbs, "#E60028"),
        Artwork(listOf("大华银行（新加坡）", "UOB Singapore", "UOB SG", "UOB", "大华银行", "大華銀行"), R.drawable.ic_institution_uob, "#003B71"),
        Artwork(listOf("盈透证券", "Interactive Brokers", "IBKR", "盈透", "盈透證券"), R.drawable.ic_institution_ibkr, "#D71920"),
        Artwork(listOf("大通银行", "Chase", "JPMorgan Chase", "JP Morgan", "大通銀行"), R.drawable.ic_institution_chase, "#117ACA"),
        Artwork(listOf("美国银行", "Bank of America", "BofA", "BOA", "美國銀行"), R.drawable.ic_institution_bofa, "#E31837"),
        Artwork(listOf("SoFi", "SoFi", "Social Finance"), R.drawable.ic_institution_sofi, "#00A5AA"),
        Artwork(listOf("嘉信理财", "Charles Schwab", "Schwab", "嘉信", "嘉信理財"), R.drawable.ic_institution_schwab, "#00A0DF"),
        Artwork(listOf("第一资本", "Capital One", "CapitalOne", "第一資本"), R.drawable.ic_institution_capital_one, "#004977"),
        Artwork(listOf("美国运通（美国）", "American Express US", "AMEX US", "AMEX", "American Express", "美国运通", "美國運通"), R.drawable.ic_institution_amex_us, "#006FCF"),
        Artwork(listOf("花旗银行（美国）", "Citibank US", "Citi US", "Citibank", "Citi", "花旗银行", "花旗銀行"), R.drawable.ic_institution_citi_us, "#056DAE"),
        Artwork(listOf("富国银行", "Wells Fargo", "富國銀行"), R.drawable.ic_institution_wells_fargo, "#D71E28"),
        Artwork(listOf("富达投资", "Fidelity Investments", "Fidelity", "富達投資"), R.drawable.ic_institution_fidelity, "#007A3E"),
        Artwork(listOf("先锋领航", "Vanguard", "先鋒領航"), R.drawable.ic_institution_vanguard, "#96151D"),
        Artwork(listOf("Robinhood", "Robinhood", "罗宾汉", "羅賓漢"), R.drawable.ic_institution_robinhood, "#00C805"),
        Artwork(listOf("富途证券", "Futu", "Futu", "富途", "富途證券", "富途牛牛", "Futubull"), R.drawable.ic_institution_futu, "#FF6900"),
        Artwork(listOf("moomoo", "moomoo", "Moomoo SG", "Moomoo US"), R.drawable.ic_institution_moomoo, "#FF6A00"),
        Artwork(listOf("老虎证券", "Tiger Brokers", "Tiger Trade", "老虎證券"), R.drawable.ic_institution_tiger, "#FFD200"),
        Artwork(listOf("长桥证券", "Longbridge", "Longbridge Securities", "LongPort", "长桥", "長橋", "長橋證券"), R.drawable.ic_institution_longbridge, "#00B8B8"),
        Artwork(listOf("微牛证券", "Webull", "微牛", "微牛證券"), R.drawable.ic_institution_webull, "#1A5AF5"),
        Artwork(listOf("雪盈证券", "Snowball X", "Snowball Securities", "雪盈", "雪盈證券"), R.drawable.ic_institution_snowball, "#1F8FFF"),
        Artwork(listOf("盈立证券", "uSMART", "uSmart Securities", "盈立", "盈立證券"), R.drawable.ic_institution_usmart, "#1E6BFF"),
        Artwork(listOf("华盛通", "VBrokers", "Valuable Capital", "华盛证券", "華盛通", "華盛證券"), R.drawable.ic_institution_vbrokers, "#E60012"),
        Artwork(listOf("耀才证券", "Bright Smart Securities", "Bright Smart", "耀才", "耀才證券"), R.drawable.ic_institution_bright_smart, "#F08300"),
        Artwork(listOf("东方财富证券", "East Money Securities", "East Money", "Eastmoney", "东方财富", "東方財富", "東方財富證券", "东财"), R.drawable.ic_institution_eastmoney, "#FF5000"),
        Artwork(listOf("华泰证券", "Huatai Securities", "HTSC", "華泰證券", "涨乐财富通"), R.drawable.ic_institution_huatai, "#E60012"),
        Artwork(listOf("中信证券", "CITIC Securities", "中信證券", "信e投"), R.drawable.ic_institution_citic_sec, "#C7000B"),
        Artwork(listOf("招商证券", "China Merchants Securities", "招商證券"), R.drawable.ic_institution_cms_sec, "#C8102E"),
        Artwork(listOf("国泰海通证券", "Guotai Haitong Securities", "Guotai Junan", "GTJA", "国泰君安", "國泰君安", "国泰海通", "國泰海通", "君弘"), R.drawable.ic_institution_gtja, "#E60012"),
    )
    private val ordered = items.flatMap { item -> item.searchTerms.map { it to item } }.sortedByDescending { it.first.length }
    fun findLogo(label: String): Artwork? {
        ordered.firstOrNull { (name, _) -> name.equals(label, ignoreCase = true) }?.let { return it.second }
        return ordered.firstOrNull { (name, _) ->
            if (name.any { it.code > 127 }) label.contains(name, true)
            else Regex("(?i)(?<![a-z0-9])" + Regex.escape(name) + "(?![a-z0-9])").containsMatchIn(label)
        }?.second
    }
}
