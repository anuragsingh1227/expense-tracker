package com.expensetracker.sms.parser

/**
 * Local, offline mapping of merchant keywords → (canonical name, category).
 * Match is case-insensitive substring. Order matters: more specific first.
 */
object MerchantDictionary {

    data class Entry(val keyword: String, val displayName: String, val category: String)

    private val entries: List<Entry> = listOf(
        // Quick commerce / groceries BEFORE generic food apps (e.g. Instamart before Swiggy)
        Entry("INSTAMART", "Swiggy Instamart", Categories.GROCERIES),
        Entry("SWIGGY INSTAMART", "Swiggy Instamart", Categories.GROCERIES),
        Entry("BLINKIT", "Blinkit", Categories.GROCERIES),
        Entry("ZEPTO", "Zepto", Categories.GROCERIES),
        Entry("BIGBASKET", "BigBasket", Categories.GROCERIES),
        Entry("BIG BASKET", "BigBasket", Categories.GROCERIES),
        Entry("DMART", "DMart", Categories.GROCERIES),
        Entry("D-MART", "DMart", Categories.GROCERIES),
        Entry("JIOMART", "JioMart", Categories.GROCERIES),
        Entry("JIO MONEY", "JioMoney", Categories.RECHARGE),
        Entry("BD JIO MONEY", "JioMoney", Categories.RECHARGE),
        Entry("NATURES BASKET", "Nature's Basket", Categories.GROCERIES),
        Entry("MORE SUPERMARKET", "More Supermarket", Categories.GROCERIES),
        Entry("BBNOW", "BigBasket Now", Categories.GROCERIES),
        Entry("BB NOW", "BigBasket Now", Categories.GROCERIES),

        // Food delivery / restaurants
        Entry("SWIGGY", "Swiggy", Categories.FOOD),
        Entry("ZOMATO", "Zomato", Categories.FOOD),
        Entry("EATSURE", "EatSure", Categories.FOOD),
        Entry("DOMINO", "Domino's", Categories.FOOD),
        Entry("PIZZAHUT", "Pizza Hut", Categories.FOOD),
        Entry("PIZZA HUT", "Pizza Hut", Categories.FOOD),
        Entry("MCDONALD", "McDonald's", Categories.FOOD),
        Entry("KFC", "KFC", Categories.FOOD),
        Entry("STARBUCKS", "Starbucks", Categories.FOOD),
        Entry("BURGER KING", "Burger King", Categories.FOOD),

        // Loan EMI collectors (ACH/NACH) — before generic bank spend matches
        Entry("HDFC BANK LTD", "HDFC Bank", Categories.EMI),
        Entry("HDFC BANK LIMITED", "HDFC Bank", Categories.EMI),
        Entry("BAJAJ FINANCE", "Bajaj Finance", Categories.EMI),
        Entry("BAJAJ FINSERV", "Bajaj Finserv", Categories.EMI),
        Entry("TATA CAPITAL", "Tata Capital", Categories.EMI),
        Entry("HOME CREDIT", "Home Credit", Categories.EMI),
        Entry("FULLERTON", "Fullerton", Categories.EMI),

        // Investments / brokers / MF / wealth
        Entry("SCRIPBOXWEALTHMANAGE", "Scripbox", Categories.INVESTMENT),
        Entry("SCRIPBOX WEALTH", "Scripbox", Categories.INVESTMENT),
        Entry("SCRIPBOX", "Scripbox", Categories.INVESTMENT),
        Entry("WEALTHMANAGE", "Wealth Manager", Categories.INVESTMENT),
        Entry("WEALTH MANAGE", "Wealth Manager", Categories.INVESTMENT),
        Entry("GROWW", "Groww", Categories.INVESTMENT),
        Entry("ZERODHA", "Zerodha", Categories.INVESTMENT),
        Entry("UPSTOX", "Upstox", Categories.INVESTMENT),
        Entry("ANGEL ONE", "Angel One", Categories.INVESTMENT),
        Entry("ANGELONE", "Angel One", Categories.INVESTMENT),
        Entry("ANGELBROKING", "Angel One", Categories.INVESTMENT),
        Entry("KUVERA", "Kuvera", Categories.INVESTMENT),
        Entry("FISDOM", "Fisdom", Categories.INVESTMENT),
        Entry("INDMONEY", "INDmoney", Categories.INVESTMENT),
        Entry("IND MONEY", "INDmoney", Categories.INVESTMENT),
        Entry("FUNDSINDIA", "FundsIndia", Categories.INVESTMENT),
        Entry("FUNDS INDIA", "FundsIndia", Categories.INVESTMENT),
        Entry("COIN BY ZERODHA", "Coin", Categories.INVESTMENT),
        Entry("PAYTM MONEY", "Paytm Money", Categories.INVESTMENT),
        Entry("ETMONEY", "ET Money", Categories.INVESTMENT),
        Entry("ET MONEY", "ET Money", Categories.INVESTMENT),
        Entry("MOTILALOSWALMF", "Motilal Oswal MF", Categories.INVESTMENT),
        Entry("MOTILAL OSWAL MF", "Motilal Oswal MF", Categories.INVESTMENT),
        Entry("MOTILALOSWAL", "Motilal Oswal", Categories.INVESTMENT),
        Entry("MOTILAL OSWAL", "Motilal Oswal", Categories.INVESTMENT),
        Entry("MOAMC", "Motilal Oswal", Categories.INVESTMENT),
        Entry("SMALLCASE", "smallcase", Categories.INVESTMENT),
        Entry("INDIANCLEARING", "NSE/BSE", Categories.INVESTMENT),
        Entry("NSCCL", "NSE Clearing", Categories.INVESTMENT),
        Entry("CDSL", "CDSL", Categories.INVESTMENT),
        Entry("NSDL", "NSDL", Categories.INVESTMENT),
        Entry("MUTUAL FUND", "Mutual Fund", Categories.INVESTMENT),
        Entry("FOR SIP", "SIP", Categories.INVESTMENT),
        Entry("SIP OF", "SIP", Categories.INVESTMENT),

        // Shopping
        Entry("AMAZON", "Amazon", Categories.SHOPPING),
        Entry("FLIPKART", "Flipkart", Categories.SHOPPING),
        Entry("MYNTRA", "Myntra", Categories.SHOPPING),
        Entry("AJIO", "Ajio", Categories.SHOPPING),
        Entry("MEESHO", "Meesho", Categories.SHOPPING),
        Entry("NYKAA", "Nykaa", Categories.SHOPPING),
        Entry("RELIANCE DIGITAL", "Reliance Digital", Categories.SHOPPING),
        Entry("RELIANCE", "Reliance Retail", Categories.SHOPPING),
        Entry("CROMA", "Croma", Categories.SHOPPING),
        Entry("TATA CLIQ", "Tata CLiQ", Categories.SHOPPING),
        Entry("TATACLIQ", "Tata CLiQ", Categories.SHOPPING),
        Entry("SNAPDEAL", "Snapdeal", Categories.SHOPPING),
        Entry("DECATHLON", "Decathlon", Categories.SHOPPING),
        Entry("IKEA", "IKEA", Categories.SHOPPING),

        // Transport / cabs
        Entry("UBER", "Uber", Categories.TRANSPORT),
        Entry("OLA", "Ola", Categories.TRANSPORT),
        Entry("RAPIDO", "Rapido", Categories.TRANSPORT),
        Entry("IRCTC", "IRCTC", Categories.TRAVEL),
        Entry("REDBUS", "RedBus", Categories.TRAVEL),
        Entry("MAKEMYTRIP", "MakeMyTrip", Categories.TRAVEL),
        Entry("GOIBIBO", "Goibibo", Categories.TRAVEL),
        Entry("EASEMYTRIP", "EaseMyTrip", Categories.TRAVEL),
        Entry("YATRA", "Yatra", Categories.TRAVEL),
        Entry("CLEARTRIP", "Cleartrip", Categories.TRAVEL),
        Entry("INDIGO", "IndiGo", Categories.TRAVEL),
        Entry("AIRINDIA", "Air India", Categories.TRAVEL),

        // Fuel
        Entry("INDIAN OIL", "Indian Oil", Categories.FUEL),
        Entry("INDIANOIL", "Indian Oil", Categories.FUEL),
        Entry("IOCL", "Indian Oil", Categories.FUEL),
        Entry("HPCL", "HP Petrol", Categories.FUEL),
        Entry("BHARAT PETROLEUM", "Bharat Petroleum", Categories.FUEL),
        Entry("BPCL", "Bharat Petroleum", Categories.FUEL),
        Entry("SHELL", "Shell", Categories.FUEL),
        Entry("NAYARA", "Nayara Energy", Categories.FUEL),

        // Entertainment / subscriptions
        Entry("NETFLIX", "Netflix", Categories.SUBSCRIPTION),
        Entry("SPOTIFY", "Spotify", Categories.SUBSCRIPTION),
        Entry("HOTSTAR", "Disney+ Hotstar", Categories.SUBSCRIPTION),
        Entry("PRIME VIDEO", "Amazon Prime", Categories.SUBSCRIPTION),
        Entry("YOUTUBE PREMIUM", "YouTube Premium", Categories.SUBSCRIPTION),
        Entry("SONYLIV", "SonyLIV", Categories.SUBSCRIPTION),
        Entry("JIO CINEMA", "JioCinema", Categories.SUBSCRIPTION),
        Entry("BOOKMYSHOW", "BookMyShow", Categories.ENTERTAINMENT),
        Entry("PVR", "PVR", Categories.ENTERTAINMENT),
        Entry("INOX", "INOX", Categories.ENTERTAINMENT),

        // Utilities / telecom
        Entry("JIO", "Jio", Categories.RECHARGE),
        Entry("AIRTEL", "Airtel", Categories.RECHARGE),
        Entry("BSNL", "BSNL", Categories.RECHARGE),
        Entry("VI ", "Vodafone Idea", Categories.RECHARGE),
        Entry("VODAFONE", "Vodafone Idea", Categories.RECHARGE),
        Entry("BESCOM", "BESCOM", Categories.UTILITIES),
        Entry("TATA POWER", "Tata Power", Categories.UTILITIES),
        Entry("ADANI ELECTRICITY", "Adani Electricity", Categories.UTILITIES),
        Entry("INDIAN OIL GAS", "Indane Gas", Categories.UTILITIES),
        Entry("BHARAT GAS", "Bharat Gas", Categories.UTILITIES),

        // Medical
        Entry("APOLLO", "Apollo Pharmacy", Categories.MEDICAL),
        Entry("1MG", "Tata 1mg", Categories.MEDICAL),
        Entry("PHARMEASY", "PharmEasy", Categories.MEDICAL),
        Entry("NETMEDS", "Netmeds", Categories.MEDICAL),
        Entry("PRACTO", "Practo", Categories.MEDICAL),

        // FASTag
        Entry("FASTAG", "FASTag", Categories.TRANSPORT),
        Entry("NHAI", "NHAI FASTag", Categories.TRANSPORT),
    )

    fun match(text: String): Entry? {
        val upper = text.uppercase()
        return entries.firstOrNull { upper.contains(it.keyword) }
    }
}

/** Pluggable merchant → category lookup used by [SmsParser]. */
fun interface MerchantMatcher {
    fun match(text: String): MerchantDictionary.Entry?
}

object DefaultMerchantMatcher : MerchantMatcher {
    override fun match(text: String): MerchantDictionary.Entry? = MerchantDictionary.match(text)
}
