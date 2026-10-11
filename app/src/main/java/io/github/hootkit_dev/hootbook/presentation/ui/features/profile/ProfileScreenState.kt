package io.github.hootkit_dev.hootbook.presentation.ui.features.profile

import android.net.Uri
import androidx.compose.ui.graphics.Color
import dev.hootkit.core.*

data class ProfileScreenState(
    val userName: String = "",
    val profileImageUri: Uri? = null,
    val profileBackgroundColor: Color = Color.Transparent,
    val bannerImageUri: Uri? = null,
    val overview: AccountsOverview? = null,
    val monthly: LedgerSummary? = null,
    val subscriptions: SubscriptionSummary? = null,
    val totalTransactions: UInt = 0u,
    val customCurrencies: List<CustomCurrency> = emptyList(),
    val contacts: List<Person> = emptyList(),
    val isLoading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val isEditSheetOpen: Boolean = false,
    val isAddPersonSheetOpen: Boolean = false,
    val editState: EditProfileState = EditProfileState(),
)
data class EditProfileState(
    val editedUserName: String = "",
    val editedProfileImageUri: Uri? = null,
    val editedProfileBackgroundColor: Color = Color.Transparent,
    val editedBannerImageUri: Uri? = null,
    val hasChanges: Boolean = false,
)
