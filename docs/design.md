# Hootbook Design System

## Overview
Hootbook follows Material 3 design principles with a focus on simplicity, clarity, and user-centric design. This document outlines the visual design system, theming approach, and layout guidelines.

## Theme System

### Material You Integration
Hootbook leverages Android 12+ dynamic color system to create personalized experiences:
- **Dynamic Color**: Automatically derives colors from user's wallpaper
- **Adaptive Theming**: Seamlessly adapts to system-wide theme preferences
- **Fallback Colors**: Provides branded colors for older Android versions

### Light & Dark Themes

#### Light Theme
```kotlin
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color(0xFF000000)
)
```

#### Dark Theme
```kotlin
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    scrim = Color(0xFF000000)
)
```

### Theme Implementation
```kotlin
@Composable
fun HootbookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
```

### Color Semantic Usage
- **Primary**: Main actions, FAB, primary buttons
- **Primary Container**: Selected states, highlights
- **Secondary**: Supporting actions, filter chips
- **Secondary Container**: Input fields, secondary selections
- **Tertiary**: Additional accents, special states
- **Tertiary Container**: Tags, badges
- **Surface**: Cards, sheets, dialogs
- **Surface Variant**: Subtle backgrounds, dividers
- **Background**: Screen backgrounds
- **Error**: Error states, destructive actions
- **Error Container**: Error backgrounds, warnings
- **Outline**: Borders, dividers
- **Outline Variant**: Subtle borders, inactive states

## Typography

### Type Scale
```kotlin
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
)
```

### Typography Usage
- **Display**: Monthly total amounts
- **Headline**: Screen titles, section headers
- **Title**: Card headers, dialog titles
- **Body**: Transaction descriptions, general content
- **Label**: Buttons, chips, navigation

## Layout System

### Spacing & Padding
Based on 8dp grid system:
```kotlin
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}
```

### Standard Margins
- **Screen padding**: 16.dp (standard Android margin)
- **Card padding**: 16.dp
- **List item padding**: 16.dp horizontal, 12.dp vertical
- **Component spacing**: 8.dp between related items

### Safe Areas
```kotlin
@Composable
fun SafeScreen(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .navigationBarsPadding()
    ) {
        content()
    }
}
```

## Component Design

### Cards
```kotlin
@Composable
fun TransactionCard(
    transaction: Transaction,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Content
        }
    }
}
```

### Bottom Navigation
- Maximum 5 destinations
- Active indicator with primary color
- Icon + label for clarity

### Floating Action Button
- Primary action only (Add transaction manually)
- Bottom-right placement
- Extended FAB on scroll

## Screen Layouts

### Home Screen Layout
```
┌─────────────────────────┐
│   Status Bar (System)   │
├─────────────────────────┤
│   App Bar (Optional)    │
├─────────────────────────┤
│   Month Summary Card    │
│   - Total Amount        │
│   - Trend Indicator     │
├─────────────────────────┤
│   Category Chips        │
│   (Horizontal Scroll)   │
├─────────────────────────┤
│   Recent Transactions   │
│   - LazyColumn          │
│   - Grouped by Date     │
│                         │
│                    FAB  │
├─────────────────────────┤
│   Bottom Navigation     │
└─────────────────────────┘
```

### Responsive Breakpoints
```kotlin
enum class WindowSize {
    COMPACT,   // < 600dp (phones)
    MEDIUM,    // 600-840dp (tablets)
    EXPANDED   // > 840dp (large tablets)
}

@Composable
fun rememberWindowSize(): WindowSize {
    val configuration = LocalConfiguration.current
    return when {
        configuration.screenWidthDp < 600 -> WindowSize.COMPACT
        configuration.screenWidthDp < 840 -> WindowSize.MEDIUM
        else -> WindowSize.EXPANDED
    }
}
```

## Accessibility

### Color Contrast
- Ensure WCAG AA compliance (4.5:1 for normal text)
- Test with color blindness simulators
- Provide high contrast theme option

### Touch Targets
- Minimum 48dp x 48dp for interactive elements
- Adequate spacing between targets
- Clear visual feedback on interaction

### Content Description
```kotlin
IconButton(
    onClick = { /* action */ },
    modifier = Modifier.semantics {
        contentDescription = "Add new transaction"
    }
) {
    Icon(Icons.Default.Add, contentDescription = null)
}
```

## Motion & Animation

### Transition Patterns
```kotlin
object Animations {
    val QuickTransition = 200
    val StandardTransition = 300
    val SlowTransition = 500
    
    val EasingStandard = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val EasingDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val EasingAccelerate = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
}
```

### Common Animations
- **List item appearance**: Fade + slide
- **Screen transitions**: Shared element when possible
- **Loading states**: Skeleton screens over spinners
- **FAB transformation**: Scale + fade

## Icons & Imagery

### Icon Style
- Material Symbols (outlined variant)
- Consistent 24dp size
- Tinted with appropriate color roles

### Common Icons
```kotlin
object HootbookIcons {
    val Home = Icons.Outlined.Home
    val Transactions = Icons.Outlined.Receipt
    val Analytics = Icons.Outlined.Analytics
    val Settings = Icons.Outlined.Settings
    val Add = Icons.Outlined.Add
    val Category = Icons.Outlined.Category
    val Calendar = Icons.Outlined.CalendarMonth
    val Search = Icons.Outlined.Search
}
```

## Shape System

### Shape Scale
```kotlin
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
```

### Shape Usage
- **Extra Small**: Chips, small buttons
- **Small**: Cards, list items
- **Medium**: Dialogs, sheets
- **Large**: Navigation drawer, large cards
- **Extra Large**: Full-screen modals

## Empty States

### Design Principles
- Helpful illustration or icon
- Clear message explaining the state
- Action button when applicable

### Example
```kotlin
@Composable
fun EmptyTransactions() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Receipt,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No transactions yet",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            "Transactions will appear here once detected from SMS",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

## Testing Themes

### Preview Configuration
```kotlin
@Preview(name = "Light Theme")
@Preview(name = "Dark Theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ComponentPreview() {
    HootbookTheme {
        // Component
    }
}
```

### Theme Testing Checklist
- [ ] Light theme readability
- [ ] Dark theme contrast
- [ ] Dynamic color adaptation
- [ ] High contrast mode support
- [ ] Font scaling (up to 200%)
- [ ] Landscape orientation
- [ ] Different screen sizes

## Best Practices

### Do's
- ✅ Respect system theme preference
- ✅ Use semantic color roles
- ✅ Test on various devices and themes
- ✅ Maintain consistent spacing
- ✅ Follow Material 3 guidelines

### Don'ts
- ❌ Hard-code colors
- ❌ Ignore safe areas
- ❌ Use custom themes without testing
- ❌ Create inconsistent layouts
- ❌ Override system preferences without user consent

## Material 3 Expressive conventions

The theme is `MaterialExpressiveTheme` with `MotionScheme.expressive()`. Use the shared components below rather than
re-styling Material ones per screen.

| Need | Use | Notes |
|---|---|---|
| Secondary screen top bar | `CustomTitleTopAppBar` | `LargeFlexibleTopAppBar` (two scroll behaviors) or `TopAppBar` (one); tonal surface → surfaceContainer on scroll, no blur |
| Back / icon-only actions | `NavigationContent`, `TooltipIconButton` | Standard icon buttons with a plain tooltip; never wrap them in a second clickable |
| Main navigation | `ShortNavigationBar` / collapsed `WideNavigationRail` | Rail from 600 dp; content inset `NavigationRailWidth` (96 dp) |
| One choice of a few | `GenericTypeSwitcher` | M3E connected button group (ToggleButtons, radio semantics); `SingleChoiceSegmentedButtonRow` with `icon = {}` in forms |
| On/off setting | `PreferenceSwitch`, `HootbookSwitch` | Thumb icon; a row that is one setting toggles as a whole (`ListItem(toggled, onToggle)` / `toggleable`) |
| Checkbox | `HootbookCheckbox` | Material 3 `Checkbox` |
| Search field | `SearchBarBox` | Pill on surfaceContainerHigh, onSurfaceVariant placeholder |
| Buttons | Material buttons with `shapes = ButtonDefaults.shapes()` / `IconButtonDefaults.shapes()` | Shape morph on press; no hardcoded button shapes |
| Loading | `LoadingIndicator`, `LinearWavyProgressIndicator` (`Loading.kt`) | Plain indicators only for meters (budget use, category share) |
| Result + undo | Standard `Snackbar` with an action | No restyled snackbars, no persistent undo bars, no Toasts |
| One FAB per screen | Add FAB in `HootbookNavHost` | Screen-specific actions (export) go in the top bar |
| Amount colors | `transactionTypeColor()`, `successColor`, `warningColor` | Theme-aware via `isAppInDarkTheme`, never `isSystemInDarkTheme()` (the app theme can differ from the system's) |
| Text | `MaterialTheme.typography` roles | No `fontSize = N.sp` on `Text` |

Screen transitions and shared-element bounds keep short `tween`s (`MotionDurations`) on purpose: springs keep both
screens composed longer, which the Transactions tab benchmark measures.


### Navigation Components
```kotlin
// For phones (compact); the app uses the M3E ShortNavigationBar (see conventions above)
NavigationBar {
    destinations.forEach { destination ->
        NavigationBarItem(
            selected = currentDestination == destination,
            onClick = { navigate(destination) },
            icon = { Icon(destination.icon, contentDescription = null) },
            label = { Text(destination.label) }
        )
    }
}

// For tablets (medium/expanded)
NavigationRail {
    destinations.forEach { destination ->
        NavigationRailItem(
            selected = currentDestination == destination,
            onClick = { navigate(destination) },
            icon = { Icon(destination.icon, contentDescription = null) },
            label = { Text(destination.label) }
        )
    }
}
```

### Adaptive Navigation
```kotlin
@Composable
fun AdaptiveNavigation(
    windowSize: WindowSize,
    currentDestination: Destination,
    onNavigate: (Destination) -> Unit
) {
    when (windowSize) {
        WindowSize.COMPACT -> {
            NavigationBar {
                // Navigation items
            }
        }
        WindowSize.MEDIUM, WindowSize.EXPANDED -> {
            NavigationRail {
                // Navigation items
            }
        }
    }
}
```

## Custom Color Extensions
```kotlin
// Custom semantic colors for specific use cases
val ColorScheme.success: Color
    @Composable
    get() = if (isSystemInDarkTheme()) Color(0xFF4CAF50) else Color(0xFF2E7D32)

val ColorScheme.warning: Color
    @Composable
    get() = if (isSystemInDarkTheme()) Color(0xFFFFA726) else Color(0xFFF57C00)

val ColorScheme.income: Color
    @Composable
    get() = success

val ColorScheme.expense: Color
    @Composable
    get() = error
```

## Resources
- [Material 3 Design Kit](https://www.figma.com/community/file/1035203688168086460)
- [Material Theme Builder](https://m3.material.io/theme-builder)
- [Color Contrast Checker](https://webaim.org/resources/contrastchecker/)
- [Material Symbols](https://fonts.google.com/icons)
- [Material 3 Components](https://developer.android.com/jetpack/compose/designsystems/material3)
- [Dynamic Color](https://developer.android.com/develop/ui/views/theming/dynamic-colors)

## Chinese localization and interface refinement

The native Compose refinement draws design cues from [InstallerX Revived](https://github.com/wxxsfxyzm/InstallerX-Revived), specifically its Material 3 Expressive settings and `PreferredPage` segmented, titled groups. This is an independent implementation using Hootbook's existing components, with no additional UI framework or copied reference code.

- Settings use labeled groups for personalization, finances, automation, and app information. Existing rounded segmented rows remain, with tonal icon containers that follow dynamic, light, and dark themes.
- Group titles expose heading semantics. Shared list rows have a 56dp minimum height, button roles when actionable, and merged text semantics. Supporting text uses the 14sp body scale and can wrap.
- The profile summary is stable rather than alternating on a timer. Language values are width-bounded and wrap to keep the main label visible.
- Large app-bar titles use the theme headline scale. Home actions use Material icon buttons with 48dp touch targets. Balance labels and supporting text avoid low-opacity text over translucent backgrounds.
- Chinese, Japanese, and Korean configurations use zero extra letter spacing throughout the theme type scale; the chosen font and Android's glyph fallback are preserved.

Verification targets are Chinese and English, light and dark themes, narrow screens, and enlarged system fonts. The existing currency and account data remain governed by their own preferences and models.
