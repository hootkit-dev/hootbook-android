package io.github.hootkit_dev.hootbook.presentation.core.plans

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hootkit_dev.hootbook.core.ui.CorePlatformAssets
import javax.inject.Inject

@HiltViewModel
class CorePlanPlatformViewModel @Inject constructor(val assets:CorePlatformAssets):ViewModel()
