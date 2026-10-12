package io.github.hootkit_dev.hootbook.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * Names to show for the built-in categories and subcategories. They are stored in English (the
 * name is how transactions, budgets and templates refer to them, and code looks some of them up),
 * so they are translated only when shown. A name the user typed has no entry and shows as typed;
 * brand names (Netflix, Uber…) have none either.
 */
object CategoryNames {
    fun display(name: String?, locale: Locale = Locale.getDefault()): String {
        if (name.isNullOrEmpty() || locale.language != "zh") return name.orEmpty()
        val simplified = HANS[name] ?: return name
        return if (isTraditional(locale)) HANT[name] ?: simplified else simplified
    }

    /** Whether [query] matches [name] as stored or as shown. */
    fun matches(name: String, query: String, locale: Locale = Locale.getDefault()): Boolean =
        name.contains(query, ignoreCase = true) || display(name, locale).contains(query, ignoreCase = true)

    private val HANS by lazy { HANS_ENTRIES.toMap() }
    private val HANT by lazy { HANT_ENTRIES.toMap() }

    private fun isTraditional(locale: Locale) =
        locale.script == "Hant" || (locale.script.isEmpty() && locale.country in setOf("TW", "HK", "MO"))

    // Lists, not maps, so a test can catch a name entered twice (a map would keep the last)
    internal val HANS_ENTRIES = listOf(
        // Categories
        "Bill" to "账单", "Borrowed" to "借入", "Business" to "生意", "Cash Withdrawal" to "取现",
        "Children" to "子女", "Credit Bill" to "信用卡还款", "Donation" to "捐赠", "EMI" to "分期",
        "Entertainment" to "娱乐", "Events" to "活动", "Fitness" to "健身", "Food & Drinks" to "餐饮",
        "Gift" to "礼物", "Groceries" to "买菜", "Hidden Charges" to "隐性费用", "Home" to "居家",
        "Income" to "收入", "Insurance" to "保险", "Investment" to "投资", "Lent" to "借出",
        "Medical" to "医疗", "Miscellaneous" to "其他", "Personal" to "个人", "Pet Care" to "宠物",
        "Savings" to "储蓄", "Self Transfer" to "自己转账", "Services" to "服务", "Shopping" to "购物",
        "Subscription" to "订阅", "Support" to "赡养", "Tax" to "税费", "Top-up" to "充值",
        "Transport" to "交通", "Travel" to "旅行",
        // Subcategories
        "Activities" to "活动", "Advisor" to "顾问费", "Appliances" to "家电", "Assets" to "资产",
        "Auto" to "三轮车", "Badminton" to "羽毛球", "Bakery" to "烘焙", "Beverages" to "饮料",
        "Bike" to "摩托车", "Birthday" to "生日", "Bonus" to "奖金", "Books" to "书籍",
        "Bowling" to "保龄球", "Bus" to "公交", "Cab" to "打车", "Camping" to "露营", "Care" to "护理",
        "Carpenter" to "木工", "Classes" to "课程", "Classes Fee" to "课程费", "Cleaning" to "清洁",
        "Clinic" to "诊所", "Clothes" to "服装", "College Fee" to "大学学费", "Commute" to "通勤",
        "Construction" to "建材", "Cook" to "厨师", "Cosmetics" to "化妆品", "Courier" to "快递",
        "Credit Card" to "信用卡", "Cricket" to "板球", "Crypto" to "加密货币", "DTH" to "电视",
        "Dad" to "爸爸", "Dairy" to "乳制品", "Date" to "约会", "Decor" to "装饰", "Dentist" to "牙医",
        "Deposit" to "存款", "Devotional" to "宗教", "Driver" to "司机", "Eating out" to "外食",
        "Education" to "教育", "Eggs" to "鸡蛋", "Electrician" to "电工", "Electricity" to "电费",
        "Electronics" to "数码", "Equipment" to "器材", "Essentials" to "日用品", "Ev Charge" to "充电",
        "Fast Food" to "快餐", "Festival" to "节日", "Fine" to "罚款", "Fixed Deposit" to "定期存款",
        "Flights" to "机票", "Food" to "食物", "Football" to "足球", "Footwear" to "鞋",
        "Forex" to "外汇", "Freelance" to "自由职业", "Fruits" to "水果", "Fuel" to "加油",
        "Furniture" to "家具", "GST" to "消费税", "Gas" to "燃气", "Gift Cards" to "礼品卡",
        "Glasses" to "眼镜", "Gold" to "黄金", "Grooming" to "理发美容", "Gym" to "健身房",
        "Health" to "健康", "Hobbies" to "爱好", "Hospital" to "医院", "Hostel" to "宿舍",
        "Hotel" to "酒店", "House" to "房屋", "House Help" to "家政", "Hygiene" to "个人卫生",
        "IPO" to "新股", "Income Tax" to "个人所得税", "Interest" to "利息", "Internet" to "宽带",
        "Inventory" to "库存", "Jewellery" to "首饰", "Lab test" to "化验", "Laundry" to "洗衣",
        "Learning" to "学习", "Legal" to "法律", "Life" to "人寿", "Liquor" to "酒水",
        "Logistics" to "物流", "Lounge" to "休息室", "Maintenance" to "维护", "Marketing" to "营销",
        "Meat" to "肉类", "Mechanic" to "修车", "Medicines" to "药品", "Metro" to "地铁",
        "Mom" to "妈妈", "Movies" to "电影", "Mutual Funds" to "基金", "Necessities" to "必需品",
        "News" to "新闻", "Nutrition" to "营养品", "Other" to "其他", "PPF" to "公积金",
        "Painting" to "粉刷", "Parents" to "父母", "Parking" to "停车", "Party" to "聚会",
        "Pest-control" to "除虫", "Phone" to "话费", "Photographer" to "摄影", "Pizza" to "披萨",
        "Plants" to "绿植", "Plumber" to "水管工", "Pocket Money" to "零花钱", "Property Tax" to "房产税",
        "Recurring Deposit" to "零存整取", "Refund" to "退款", "Renovation" to "装修", "Rent" to "房租",
        "Repair" to "维修", "Salary" to "工资", "School Fee" to "学费", "Self-care" to "自我护理",
        "Service" to "服务", "Shows" to "演出", "Snacks" to "零食", "Software" to "软件",
        "Spiritual" to "心灵", "Spouse" to "配偶", "Staples" to "主食", "Stationery" to "文具",
        "Stocks" to "股票", "Sweets" to "甜品", "Tailor" to "裁缝", "Take Away" to "外卖",
        "Tea & Coffee" to "茶饮咖啡", "Therapy" to "心理咨询", "Tickets" to "门票", "Tiffin" to "便当",
        "Tip" to "小费", "Toiletries" to "洗护用品", "Tolls" to "过路费", "Toys" to "玩具",
        "Train" to "火车", "Tuition Fee" to "补习费", "Upkeep" to "保养", "Utensils" to "厨具",
        "Vegetables" to "蔬菜", "Vehicle" to "车辆", "Vehicle Wash" to "洗车", "Verification" to "认证费",
        "Vet" to "宠物医院", "Vices" to "烟酒", "Video games" to "游戏", "Visa fees" to "签证费",
        "Water" to "水费", "Wedding" to "婚礼", "Xerox" to "打印复印"
    )

    // Traditional Chinese, where it differs from the simplified form
    internal val HANT_ENTRIES = listOf(
        "Bill" to "帳單", "Borrowed" to "借入", "Business" to "生意", "Cash Withdrawal" to "提領現金",
        "Children" to "子女", "Credit Bill" to "信用卡還款", "Donation" to "捐贈", "EMI" to "分期",
        "Entertainment" to "娛樂", "Events" to "活動", "Fitness" to "健身", "Food & Drinks" to "餐飲",
        "Gift" to "禮物", "Groceries" to "買菜", "Hidden Charges" to "隱性費用", "Home" to "居家",
        "Income" to "收入", "Insurance" to "保險", "Investment" to "投資", "Lent" to "借出",
        "Medical" to "醫療", "Miscellaneous" to "其他", "Personal" to "個人", "Pet Care" to "寵物",
        "Savings" to "儲蓄", "Self Transfer" to "自己轉帳", "Services" to "服務", "Shopping" to "購物",
        "Subscription" to "訂閱", "Support" to "贍養", "Tax" to "稅費", "Top-up" to "儲值",
        "Transport" to "交通", "Travel" to "旅行",
        "Activities" to "活動", "Advisor" to "顧問費", "Appliances" to "家電", "Assets" to "資產",
        "Auto" to "三輪車", "Badminton" to "羽毛球", "Bakery" to "烘焙", "Beverages" to "飲料",
        "Bike" to "機車", "Birthday" to "生日", "Bonus" to "獎金", "Books" to "書籍",
        "Bowling" to "保齡球", "Bus" to "公車", "Cab" to "叫車", "Camping" to "露營", "Care" to "護理",
        "Carpenter" to "木工", "Classes" to "課程", "Classes Fee" to "課程費", "Cleaning" to "清潔",
        "Clinic" to "診所", "Clothes" to "服裝", "College Fee" to "大學學費", "Commute" to "通勤",
        "Construction" to "建材", "Cook" to "廚師", "Cosmetics" to "化妝品", "Courier" to "快遞",
        "Credit Card" to "信用卡", "Cricket" to "板球", "Crypto" to "加密貨幣", "DTH" to "電視",
        "Dad" to "爸爸", "Dairy" to "乳製品", "Date" to "約會", "Decor" to "裝飾", "Dentist" to "牙醫",
        "Deposit" to "存款", "Devotional" to "宗教", "Driver" to "司機", "Eating out" to "外食",
        "Education" to "教育", "Eggs" to "雞蛋", "Electrician" to "電工", "Electricity" to "電費",
        "Electronics" to "數位產品", "Equipment" to "器材", "Essentials" to "日用品", "Ev Charge" to "充電",
        "Fast Food" to "速食", "Festival" to "節日", "Fine" to "罰款", "Fixed Deposit" to "定期存款",
        "Flights" to "機票", "Food" to "食物", "Football" to "足球", "Footwear" to "鞋",
        "Forex" to "外匯", "Freelance" to "自由職業", "Fruits" to "水果", "Fuel" to "加油",
        "Furniture" to "家具", "GST" to "消費稅", "Gas" to "瓦斯", "Gift Cards" to "禮品卡",
        "Glasses" to "眼鏡", "Gold" to "黃金", "Grooming" to "理髮美容", "Gym" to "健身房",
        "Health" to "健康", "Hobbies" to "愛好", "Hospital" to "醫院", "Hostel" to "宿舍",
        "Hotel" to "飯店", "House" to "房屋", "House Help" to "家事服務", "Hygiene" to "個人衛生",
        "IPO" to "新股", "Income Tax" to "所得稅", "Interest" to "利息", "Internet" to "網路",
        "Inventory" to "庫存", "Jewellery" to "首飾", "Lab test" to "化驗", "Laundry" to "洗衣",
        "Learning" to "學習", "Legal" to "法律", "Life" to "人壽", "Liquor" to "酒水",
        "Logistics" to "物流", "Lounge" to "休息室", "Maintenance" to "維護", "Marketing" to "行銷",
        "Meat" to "肉類", "Mechanic" to "修車", "Medicines" to "藥品", "Metro" to "捷運",
        "Mom" to "媽媽", "Movies" to "電影", "Mutual Funds" to "基金", "Necessities" to "必需品",
        "News" to "新聞", "Nutrition" to "營養品", "Other" to "其他", "PPF" to "公積金",
        "Painting" to "粉刷", "Parents" to "父母", "Parking" to "停車", "Party" to "聚會",
        "Pest-control" to "除蟲", "Phone" to "電話費", "Photographer" to "攝影", "Pizza" to "披薩",
        "Plants" to "綠植", "Plumber" to "水電工", "Pocket Money" to "零用錢", "Property Tax" to "房屋稅",
        "Recurring Deposit" to "零存整付", "Refund" to "退款", "Renovation" to "裝修", "Rent" to "房租",
        "Repair" to "維修", "Salary" to "薪資", "School Fee" to "學費", "Self-care" to "自我照顧",
        "Service" to "服務", "Shows" to "演出", "Snacks" to "零食", "Software" to "軟體",
        "Spiritual" to "心靈", "Spouse" to "配偶", "Staples" to "主食", "Stationery" to "文具",
        "Stocks" to "股票", "Sweets" to "甜點", "Tailor" to "裁縫", "Take Away" to "外帶",
        "Tea & Coffee" to "茶飲咖啡", "Therapy" to "心理諮商", "Tickets" to "門票", "Tiffin" to "便當",
        "Tip" to "小費", "Toiletries" to "盥洗用品", "Tolls" to "過路費", "Toys" to "玩具",
        "Train" to "火車", "Tuition Fee" to "補習費", "Upkeep" to "保養", "Utensils" to "廚具",
        "Vegetables" to "蔬菜", "Vehicle" to "車輛", "Vehicle Wash" to "洗車", "Verification" to "認證費",
        "Vet" to "動物醫院", "Vices" to "菸酒", "Video games" to "遊戲", "Visa fees" to "簽證費",
        "Water" to "水費", "Wedding" to "婚禮", "Xerox" to "影印"
    )
}

/** [CategoryNames.display] in the screen's language. */
@Composable
fun categoryName(name: String?): String =
    CategoryNames.display(name, LocalConfiguration.current.locales[0])
