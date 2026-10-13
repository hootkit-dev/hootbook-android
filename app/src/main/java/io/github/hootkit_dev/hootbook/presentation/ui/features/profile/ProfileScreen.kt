package io.github.hootkit_dev.hootbook.presentation.ui.features.profile

import androidx.compose.animation.core.FastOutSlowInEasing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import io.github.hootkit_dev.hootbook.R
import dev.hootkit.core.Person
import dev.hootkit.core.PersonRelation
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionHeader
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.AddEditPersonSheet
import io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow.categoryLabel
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Calendar
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Edit2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.blue_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.blue_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.green_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.green_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.orange_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.orange_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.red_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.red_light
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation
import dev.chrisbanes.haze.HazeState
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.ProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToPerson: (String) -> Unit = {},
    animatedContentScope: AnimatedContentScope? = null,
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    val state by profileViewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }

    val errorSnackbar = remember { SnackbarHostState() }
    val operationFailed = stringResource(R.string.onboarding_operation_failed)
    LaunchedEffect(state.error) { state.error?.let { errorSnackbar.showSnackbar(operationFailed) } }
    Scaffold(
        snackbarHost = { SnackbarHost(errorSnackbar) },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorSmall,
                title = stringResource(R.string.profile),
                hazeState = hazeState,
                hasBackButton = true,
                hasActionButton = true,
                navigationContent = { NavigationContent(onNavigateBack) },
                actionContent = {
                    IconButton(
                        onClick = { profileViewModel.toggleEditSheet() },
                        shapes =  IconButtonDefaults.shapes(),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Icon(
                            imageVector = Iconax.Edit2,
                            contentDescription = stringResource(R.string.edit),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        ProfileContent(
            state = state,
            listState = listState,
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding()
            ),
            onNavigateToContacts = onNavigateToContacts,
            onNavigateToPerson = onNavigateToPerson,
            onAddPerson = { profileViewModel.showAddPersonSheet(true) },
            animatedContentScope = animatedContentScope
        )
    }

    if (state.isEditSheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        HootbookModalBottomSheet(
            onDismissRequest = { profileViewModel.dismissEditSheet() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            EditProfileSheet(
                state = state.editState,
                onNameChange = profileViewModel::updateEditUserName,
                onProfileImageChange = profileViewModel::updateEditProfileImage,
                onBackgroundColorChange = profileViewModel::updateEditProfileBackgroundColor,
                onBannerImageChange = profileViewModel::updateEditBannerImage,
                onSave = profileViewModel::saveProfileChanges,
                onCancel = profileViewModel::dismissEditSheet,
                saving = state.saving, error = state.error
            )
        }
    }

    if (state.isAddPersonSheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        HootbookModalBottomSheet(
            onDismissRequest = { profileViewModel.showAddPersonSheet(false) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            AddEditPersonSheet(
                attachmentStore = profileViewModel.attachmentStore,
                saving = state.saving, error = state.error,
                onDismiss = { profileViewModel.showAddPersonSheet(false) },
                onSave = { name, phone, notes, color, avatar, category ->
                    profileViewModel.addPerson(name, phone, notes, color, avatar, category)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.ProfileContent(
    modifier: Modifier = Modifier,
    state: ProfileScreenState,
    listState: LazyListState,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onNavigateToContacts: () -> Unit,
    onNavigateToPerson: (String) -> Unit = {},
    onAddPerson: () -> Unit = {},
    animatedContentScope: AnimatedContentScope? = null
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        //Banner image
        Box(
            modifier = Modifier.fillMaxWidth().height(250.dp)
        ){
            if ( state.bannerImageUri != null) {
                AsyncImage(
                    model =  state.bannerImageUri,
                    contentDescription = stringResource(R.string.banner),
                    modifier = Modifier.fillMaxSize().alpha(0.5f),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.banner_bg_image),
                    contentDescription = stringResource(R.string.banner),
                    modifier = Modifier.fillMaxSize().alpha(0.5f),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.align(Alignment.BottomCenter).height(80.dp).fillMaxWidth().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent,
                    MaterialTheme.colorScheme.background)
                )
            ))
        }
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
//                start = Dimensions.Padding.content,
//                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content +
                        contentPadding.calculateTopPadding(),
                bottom = 120.dp + contentPadding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DisplayProfileImagesCard(
                    profileImageUri = state.profileImageUri,
                    profileBackgroundColor = state.profileBackgroundColor,
                    state = state
                )
            }

            item {
                FinancialOverviewCard(
                    netWorth = state.overview?.netWorth?.amount,
                    income = state.monthly?.income,
                    expense = state.monthly?.expense,
                    customSymbols = state.customCurrencies.associate { it.code to it.symbol },
                    complete = state.overview?.netWorthComplete != false && state.monthly?.complete != false,
                    activeSubscriptions = state.subscriptions?.count?.toInt() ?: 0,
                    baseCurrency = state.monthly?.currency ?: state.overview?.netWorth?.currency.orEmpty(),
                    modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                )
            }

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    SectionHeader(
                        title = stringResource(R.string.contacts),
                        style = MaterialTheme.typography.titleMedium,
                        action = {
                            Text(
                                text = stringResource(R.string.view_all),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .clip(RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .clickable (onClick = onNavigateToContacts)
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    if (state.contacts.isEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                        ) {
                            Text(
                                text = stringResource(R.string.no_contacts_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Button(
                                onClick = onAddPerson,
                                modifier = Modifier.height(48.dp),
                                shapes = ButtonDefaults.shapes()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.add_person),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            contentPadding = PaddingValues(horizontal = Dimensions.Padding.content)
                        ) {
                            itemsIndexed(state.contacts, key = { _, person -> person.id }) { index, person ->
                                ContactCarouselCard(
                                    person = person,
                                    index = index + 1,
                                    onClick = { onNavigateToPerson(person.id) },
                                    animatedContentScope = animatedContentScope,
                                    sharedElementKey = "person_avatar_${person.id}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
fun DisplayProfileImagesCard(
    state: ProfileScreenState,
    profileImageUri: Uri?,
    profileBackgroundColor: Color,
) {
    Box(modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(24.dp))) {

        Column(
            modifier = Modifier.fillMaxSize().padding(0.dp, bottom = 20.dp).align(Alignment.BottomStart),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Image
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .background(profileBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                if (profileImageUri != null) {
                    AsyncImage(
                        model = profileImageUri,
                        contentDescription = stringResource(R.string.profile),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.avatar_1),
                        contentDescription = stringResource(R.string.profile),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            DisplayUserNameAndSubtitles(
                userName = state.userName,
                totalTransactions = state.totalTransactions.toInt()
            )
        }


    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DisplayUserNameAndSubtitles(userName: String, totalTransactions: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = userName,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialShapes.Cookie9Sided.toShape()
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Enabled",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Text(
            text = stringResource(R.string.transactions_count_format, totalTransactions),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun FinancialOverviewCard(
    netWorth: BigDecimal?,
    income: BigDecimal?,
    expense: BigDecimal?,
    customSymbols: Map<String, String> = emptyMap(),
    complete: Boolean = true,
    activeSubscriptions: Int,
    baseCurrency: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.financial_overview),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (!complete) Text(stringResource(R.string.core_debts_incomplete), color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FinancialItem(
                    label = stringResource(R.string.net_worth),
                    value = CoreMoneyPresentation.format(netWorth, baseCurrency, customSymbols),
                    icon = Icons.Rounded.AccountBalance,
                    color = green_light,
                    iconColor = green_dark,
                    modifier = Modifier.weight(1f)
                )
                FinancialItem(
                    label = stringResource(R.string.upcoming),
                    value =
                        if (activeSubscriptions == 1) stringResource(R.string.active_subscriptions_singular)
                        else stringResource(R.string.active_subscriptions_plural, activeSubscriptions),
                    icon = Iconax.Calendar,
                    color = orange_light,
                    iconColor = orange_dark,
                    modifier = Modifier.weight(1f)
                )

            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FinancialItem(
                    label = stringResource(R.string.expense),
                    value = CoreMoneyPresentation.format(expense, baseCurrency, customSymbols),
                    icon = Icons.AutoMirrored.Rounded.TrendingDown,
                    color = red_light,
                    iconColor = red_dark,
                    modifier = Modifier.weight(1f)
                )
                FinancialItem(
                    label = stringResource(R.string.income),
                    value = CoreMoneyPresentation.format(income, baseCurrency, customSymbols),
                    icon = Icons.AutoMirrored.Rounded.TrendingUp,
                    color = blue_light,
                    iconColor = blue_dark,
                    modifier = Modifier.weight(1f)
                )

            }
        }
    }
}

@Composable
fun FinancialItem(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().background(
            color = MaterialTheme.colorScheme.surface.copy(0.5f),
            shape = RoundedCornerShape(Dimensions.Radius.md)
        ).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier =
                Modifier.size(40.dp)
                    .clip(RoundedCornerShape(Dimensions.Radius.md))
                    .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.ContactCarouselCard(
    person: Person,
    index: Int,
    onClick: () -> Unit = {},
    animatedContentScope: AnimatedContentScope? = null,
    sharedElementKey: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val attachmentStore = remember(context) { io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore(context) }
    val avatarFile = person.avatar?.let(attachmentStore::resolve)
    val colorInt = try {
        android.graphics.Color.parseColor(person.color)
    } catch (e: Exception) {
        android.graphics.Color.parseColor("#4CAF50")
    }
    val backgroundColor = Color(colorInt)

    val sharedModifier = if (animatedContentScope != null && sharedElementKey != null) {
        Modifier.sharedBounds(
            rememberSharedContentState(key = sharedElementKey),
            animatedVisibilityScope = animatedContentScope,
            boundsTransform = { _, _ ->
                tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
            },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center
            )
        )
    } else {
        Modifier
    }

    Card(
        modifier = Modifier
            .then(sharedModifier)
            .width(120.dp)
            .height(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background: Avatar or Placeholder
            if (avatarFile != null) {
                AsyncImage(
                    model = avatarFile,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = person.name.take(1).uppercase(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.2f)
                    )
                }
            }

            // Fade effect at the bottom for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.4f to Color.Transparent,
                            1f to backgroundColor.copy(alpha = 0.8f)
                        )
                    )
            )

            // Name and Category (Bottom Center)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp, start = 8.dp, end = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = categoryLabel(person.relation ?: PersonRelation.OTHER),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
