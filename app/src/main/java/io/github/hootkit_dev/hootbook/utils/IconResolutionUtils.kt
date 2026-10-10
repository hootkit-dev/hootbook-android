package io.github.hootkit_dev.hootbook.utils

import android.content.Context
import io.github.hootkit_dev.hootbook.R

/**
 * Utility to handle stable icon resolution.
 * Instead of storing raw resource IDs (which change between builds),
 * Now we store the resource name string and resolve it at runtime.
 */
object IconResolutionUtils {

    /**
     * Resolves a resource ID to its name string.
     * Returns empty string if the resource ID is invalid.
     */
    fun resIdToName(context: Context, resId: Int): String {
        if (resId == 0) return ""
        return try {
            // Also verify it's a drawable/mipmap to be safe
            val type = context.resources.getResourceTypeName(resId)
            if (type == "drawable" || type == "mipmap") {
                context.resources.getResourceEntryName(resId)
            } else ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Checks if a resource ID exists and is a drawable/mipmap.
     * Returns the same resId if valid, otherwise returns fallbackResId.
     */
    fun getSafeResId(context: Context, resId: Int, fallbackResId: Int): Int {
        if (resId == 0) return fallbackResId
        return try {
            val type = context.resources.getResourceTypeName(resId)
            if (type == "drawable" || type == "mipmap") resId else fallbackResId
        } catch (e: Exception) {
            fallbackResId
        }
    }


    /**
     * Resolves a resource name string back to its current resource ID.
     * Returns 0 if the name cannot be resolved.
     */
    fun nameToResId(context: Context?, name: String?): Int {
        if (name.isNullOrEmpty()) return 0
        
        // Try to get identifier from string if context is provided
        context?.let { ctx ->
            val resId = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
            if (resId != 0) return resId
        }

        // Brand logos that were removed: saved categories and accounts still name them
        if (name.startsWith("ic_brand_")) return retiredBrandIcon(name)

        // Fallback for common icons if getIdentifier fails or resource was renamed
        return when (name) {
            "type_finance_money_bag" -> R.drawable.type_finance_money_bag
            "type_finance_coin" -> R.drawable.type_finance_coin
            "type_food_stuffed_flatbread" -> R.drawable.type_food_stuffed_flatbread
            "type_travel_transport_airplane" -> R.drawable.type_travel_transport_airplane
            "type_shopping_shopping_bags" -> R.drawable.type_shopping_shopping_bags
            "type_groceries_bread" -> R.drawable.type_groceries_bread
            "type_event_and_place_house" -> R.drawable.type_event_and_place_house
            "type_snack_popcorn" -> R.drawable.type_snack_popcorn
            "type_event_and_place_party_popper" -> R.drawable.type_event_and_place_party_popper
            "type_travel_transport_luggage" -> R.drawable.type_travel_transport_luggage
            "type_health_pill" -> R.drawable.type_health_pill
            "type_tool_electronic_scissors" -> R.drawable.type_tool_electronic_scissors
            "type_sports_baseball" -> R.drawable.type_sports_baseball
            "type_tool_electronic_high_voltage" -> R.drawable.type_tool_electronic_high_voltage
            "type_travel_transport_admission_tickets" -> R.drawable.type_travel_transport_admission_tickets
            "type_tool_electronic_clapper_board" -> R.drawable.type_tool_electronic_clapper_board
            "type_travel_transport_automobile" -> R.drawable.type_travel_transport_automobile
            "type_stationary_card_file_box" -> R.drawable.type_stationary_card_file_box
            "type_flower_and_tree_herb" -> R.drawable.type_flower_and_tree_herb
            "type_health_stethoscope" -> R.drawable.type_health_stethoscope
            "type_health_mending_heart" -> R.drawable.type_health_mending_heart
            "type_finance_chart_decreasing" -> R.drawable.type_finance_chart_decreasing
            "type_event_and_place_houses" -> R.drawable.type_event_and_place_houses
            "type_animal_dog_face" -> R.drawable.type_animal_dog_face
            "type_finance_classical_building" -> R.drawable.type_finance_classical_building
            "type_stationary_clipboard" -> R.drawable.type_stationary_clipboard
            "type_finance_bank" -> R.drawable.type_finance_bank
            "type_sports_bullseye" -> R.drawable.type_sports_bullseye
            "type_stationary_wrapped_gift" -> R.drawable.type_stationary_wrapped_gift
            "type_finance_money_with_wings" -> R.drawable.type_finance_money_with_wings
            "type_health_drop_of_blood" -> R.drawable.type_health_drop_of_blood
            "type_animal_goblin" -> R.drawable.type_animal_goblin
            "type_finance_dollar_banknote" -> R.drawable.type_finance_dollar_banknote
            "type_food_dining" -> R.drawable.type_food_dining
            "type_travel_transport_taxi" -> R.drawable.type_travel_transport_taxi
            else -> 0
        }
    }

    /** A generic icon in place of a brand logo that is no longer bundled (the Indian brands). */
    private fun retiredBrandIcon(name: String): Int = when (name.removePrefix("ic_brand_")) {
        "swiggy", "zomato" -> R.drawable.type_food_takeout
        "rapido", "ola", "ola_electric", "blu_smart", "yulu", "bounce", "vogo" -> R.drawable.type_travel_transport_taxi
        "zepto", "blinkit", "bigbasket", "grofers", "dmart", "jiomart", "dunzo" -> R.drawable.type_groceries_basket
        "oyo", "makemytrip", "goibibo", "cleartrip", "yatra", "ixigo" -> R.drawable.type_event_and_place_hotel
        "jiocinema", "jiohotstar", "zee5", "sony_liv", "voot", "eros_now", "mx_player", "bookmyshow", "pvr", "inox" ->
            R.drawable.type_tool_electronic_clapper_board
        "simpl", "slice", "lazypay", "onecard", "cred" -> R.drawable.type_finance_credit_card
        "paytm", "phonepe", "bhim", "mobikwik", "freecharge", "amazon_pay", "cashfree", "juspay" -> R.drawable.type_finance_money_bag
        "airtel", "jio", "bsnl", "mtnl", "vodafone_idea" -> R.drawable.type_tool_electronic_mobile_phone
        else -> if ("bank" in name || name.endsWith("_sbi") || name.endsWith("_lic")) R.drawable.type_finance_bank
        else R.drawable.type_shopping_shopping_bags
    }
}
