package io.github.hootkit_dev.hootbook.presentation.ui.features.profile

import android.net.Uri
import androidx.compose.ui.graphics.Color
import io.github.hootkit_dev.hootbook.domain.model.LendBorrowPerson
import java.math.BigDecimal

data class ProfileScreenState(
    val userName: String = "User",
    val profileImageUri: Uri? = null,
    val profileBackgroundColor: Color = Color.Transparent,
    val bannerImageUri: Uri? = null,
    val totalTransactions: Int = 0,
    val netWorth: BigDecimal = BigDecimal.ZERO,
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val totalExpense: BigDecimal = BigDecimal.ZERO,
    val activeSubscriptions: Int = 0,
    val baseCurrency: String = "CNY",
    val contacts: List<LendBorrowPerson> = emptyList(),
    val isLoading: Boolean = false,
    val isEditSheetOpen: Boolean = false,
    val hasStoragePermission: Boolean = false,
    val isAddPersonSheetOpen: Boolean = false,
    val editState: EditProfileState = EditProfileState()
)

data class EditProfileState(
    val editedUserName: String = "",
    val editedProfileImageUri: Uri? = null,
    val editedProfileBackgroundColor: Color = Color.Transparent,
    val editedBannerImageUri: Uri? = null,
    val hasChanges: Boolean = false
)
