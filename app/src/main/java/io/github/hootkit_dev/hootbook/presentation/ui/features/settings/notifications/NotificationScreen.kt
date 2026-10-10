package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.notifications

import io.github.hootkit_dev.hootbook.utils.DateFormats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Upcoming
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.components.BrandIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.PreferenceSwitch
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionHeader
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.purple_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.purple_light
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    onNavigateBack: () -> Unit,
    notificationViewModel: NotificationViewModel = hiltViewModel(),
    blurEffects: Boolean,
) {
    val upcomingEnabled by notificationViewModel.upcomingNotificationsEnabled.collectAsStateWithLifecycle()
    val subscriptions by notificationViewModel.subscriptions.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.notifications),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = { NavigationContent(onNavigateBack) }
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = Dimensions.Padding.content,
                        end = Dimensions.Padding.content,
                        top = Dimensions.Padding.content + paddingValues.calculateTopPadding()
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Upcoming Settings
                SectionHeader(title = stringResource(R.string.upcoming_transactions_section), modifier = Modifier.padding(start = Spacing.md))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {

                    val itemsCount = subscriptions.size
                    PreferenceSwitch(
                        title = stringResource(R.string.upcoming_transactions_section),
                        subtitle = stringResource(R.string.upcoming_transactions_desc),
                        checked = upcomingEnabled,
                        onCheckedChange = notificationViewModel::setUpcomingNotificationsEnabled,
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(purple_light, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Upcoming,
                                    contentDescription = null,
                                    tint = purple_dark
                                )
                            }
                        },
                        isSingle = !upcomingEnabled || itemsCount == 0,
                        isFirst = true,
                        isLast = !upcomingEnabled || itemsCount == 0,
                        padding = PaddingValues(0.dp)
                    )

                    AnimatedVisibility(
                        visible = upcomingEnabled && subscriptions.isNotEmpty(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                            subscriptions.forEachIndexed { index, item ->
                                val isLastItem = index == subscriptions.lastIndex
                                PreferenceSwitch(
                                    title = item.subscription.merchantName,
                                    subtitle = item.subscription.nextPaymentDate?.format(DateFormats.monthDayFormatter()) ?: stringResource(R.string.no_date),
                                    checked = item.isNotificationEnabled,
                                    onCheckedChange = { 
                                        notificationViewModel.toggleSubscriptionNotification(item.subscription.id, it)
                                    },
                                    leadingIcon = {
                                        BrandIcon(
                                            merchantName = item.subscription.merchantName,
                                            category = item.subscription.category,
                                            subcategory = item.subscription.subcategory,
                                            size = 48.dp
                                        )
                                    },
                                    isFirst = false,
                                    isLast = isLastItem,
                                    isSingle = false,
                                    padding = PaddingValues(0.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

