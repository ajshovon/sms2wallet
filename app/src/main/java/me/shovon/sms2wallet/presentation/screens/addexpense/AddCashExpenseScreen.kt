package me.shovon.sms2wallet.presentation.screens.addexpense

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.shovon.sms2wallet.presentation.components.FormErrorSummary
import me.shovon.sms2wallet.presentation.components.SelectionSheet
import me.shovon.sms2wallet.presentation.model.TransactionDetailUiState
import me.shovon.sms2wallet.presentation.model.TransactionDirection
import me.shovon.sms2wallet.presentation.theme.SolarIcons
import me.shovon.sms2wallet.presentation.theme.Sms2WalletTheme
import me.shovon.sms2wallet.presentation.theme.Spacing
import me.shovon.sms2wallet.presentation.util.MoneyFormatter
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode

private val QUICK_AMOUNTS = listOf(50, 100, 500, 1000)

/**
 * High-speed manual expense entry screen inspired by modern fintech leaders (Cash App, Revolut).
 *
 * Designed around zero-friction physical ergonomics:
 * - Giant hero amount display with auto-focused keypad cursor.
 * - One-tap category chips (top categories directly reachable with a single thumb tap).
 * - One-tap wallet account chips.
 * - Sticky bottom save bar in the primary thumb zone that floats above the keyboard.
 */
@Composable
fun AddCashExpenseScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: AddCashExpenseViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AddCashExpenseContent(
        state = state,
        onOpenSettings = onOpenSettings,
        onBack = onBack,
        onSave = { viewModel.save(onSaved) },
        onAmountChange = viewModel::onAmountChange,
        onDirectionChange = viewModel::onDirectionChange,
        onCategoryChange = viewModel::onCategoryChange,
        onAccountChange = viewModel::onAccountChange,
        onMerchantChange = viewModel::onMerchantChange,
        onNoteChange = viewModel::onNoteChange
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCashExpenseContent(
    state: TransactionDetailUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onAmountChange: (String) -> Unit,
    onDirectionChange: (TransactionDirection) -> Unit,
    onCategoryChange: (String) -> Unit,
    onAccountChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var categorySheetOpen by remember { mutableStateOf(false) }
    var accountSheetOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Auto-focus the hero amount input after the entrance transition settles so the soft keyboard
    // does not collide with the screen transition and trigger window resize jitter.
    LaunchedEffect(Unit) {
        delay(250)
        focusRequester.requestFocus()
    }

    if (categorySheetOpen) {
        SelectionSheet(
            title = "Choose Category",
            options = state.availableCategories,
            selected = state.category,
            onSelect = {
                onCategoryChange(it)
                categorySheetOpen = false
            },
            onDismiss = { categorySheetOpen = false }
        )
    }

    if (accountSheetOpen) {
        SelectionSheet(
            title = "Choose Account",
            options = state.availableAccounts,
            selected = state.accountName,
            onSelect = {
                onAccountChange(it)
                accountSheetOpen = false
            },
            onDismiss = { accountSheetOpen = false }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.direction == TransactionDirection.EXPENSE) "Add Expense" else "Add Income",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(SolarIcons.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    // Quick Direction Switcher in the top bar
                    TypeToggle(
                        direction = state.direction,
                        onDirectionChange = onDirectionChange
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Sticky Bottom Save Bar (The Thumb Zone)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                border = BorderStroke(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                ) {
                    val amountNum = runCatching { BigDecimal(state.amountText.trim()) }.getOrNull()
                    val canSave = !state.isSaving && amountNum != null && amountNum.signum() > 0

                    val buttonColor by animateColorAsState(
                        targetValue = if (state.direction == TransactionDirection.EXPENSE) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Sms2WalletTheme.extendedColors.income
                        },
                        label = "save_btn_color"
                    )

                    Button(
                        onClick = onSave,
                        enabled = canSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            val actionWord = if (state.direction == TransactionDirection.EXPENSE) "Save Expense" else "Save Income"
                            val formattedAmount = amountNum?.let { MoneyFormatter.formatBdt(it) }
                            Text(
                                text = if (formattedAmount != null) "$actionWord • $formattedAmount" else "Enter an amount",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            state.errorMessage?.let { FormErrorSummary(it) }

            // 1. HERO AMOUNT SECTION
            HeroAmountCard(
                amountText = state.amountText,
                amountError = state.amountError,
                isIncome = state.direction == TransactionDirection.INCOME,
                focusRequester = focusRequester,
                onAmountChange = onAmountChange
            )

            // 2. QUICK CATEGORY SELECTOR (ONE-TAP CHIPS)
            QuickCategorySelector(
                categories = state.availableCategories,
                selectedCategory = state.category,
                onSelectCategory = onCategoryChange,
                onOpenAllCategories = { categorySheetOpen = true },
                onOpenSettings = onOpenSettings
            )

            // 3. QUICK ACCOUNT SELECTOR (ONE-TAP CHIPS)
            QuickAccountSelector(
                accounts = state.availableAccounts,
                selectedAccount = state.accountName,
                accountError = state.accountError,
                onSelectAccount = onAccountChange,
                onOpenAllAccounts = { accountSheetOpen = true },
                onOpenSettings = onOpenSettings
            )

            // 4. MERCHANT & NOTE DETAILS
            DetailsCard(
                merchant = state.merchant,
                note = state.note,
                onMerchantChange = onMerchantChange,
                onNoteChange = onNoteChange
            )

            Spacer(modifier = Modifier.height(Spacing.xxl))
        }
    }
}

/**
 * Centered hero display with huge digits, automatic cursor focus, and quick increment chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroAmountCard(
    amountText: String,
    amountError: String?,
    isIncome: Boolean,
    focusRequester: FocusRequester,
    onAmountChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        border = BorderStroke(
            width = 1.dp,
            color = if (amountError != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Currency + Amount Display
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                val takaColor by animateColorAsState(
                    targetValue = if (isIncome) {
                        Sms2WalletTheme.extendedColors.income
                    } else {
                        Sms2WalletTheme.extendedColors.expense
                    },
                    label = "taka_symbol_color"
                )

                Text(
                    text = MoneyFormatter.TAKA_SYMBOL,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = takaColor,
                    modifier = Modifier.padding(end = 4.dp)
                )

                Box(contentAlignment = Alignment.Center) {
                    if (amountText.isEmpty()) {
                        Text(
                            text = "0.00",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                    }

                    BasicTextField(
                        value = amountText,
                        onValueChange = onAmountChange,
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .semantics {
                                contentDescription = if (isIncome) "Income amount in Taka" else "Expense amount in Taka"
                            },
                        textStyle = TextStyle(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(takaColor),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        )
                    )
                }
            }

            // Error label
            amountError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Quick increment chips (+৳50, +৳100, +৳500, +৳1,000, Clear)
            // Wraps rather than scrolls. A scrolling row centred with
            // Arrangement.CenterHorizontally hides its overflow on *both* sides once the
            // content is wider than the screen, which at large font sizes put the first and
            // last chips off-screen with no affordance saying they were there.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                QUICK_AMOUNTS.forEach { inc ->
                    Surface(
                        onClick = {
                            val current = runCatching { BigDecimal(amountText.trim()) }.getOrNull() ?: BigDecimal.ZERO
                            val next = current.add(BigDecimal(inc))
                            val updated = if (next.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
                                next.toBigInteger().toString()
                            } else {
                                next.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                            }
                            onAmountChange(updated)
                        },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.semantics {
                            role = Role.Button
                            contentDescription = "Add $inc Taka"
                        }
                    ) {
                        Text(
                            text = "+৳$inc",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        )
                    }
                }

                if (amountText.isNotEmpty()) {
                    Surface(
                        onClick = { onAmountChange("") },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.semantics {
                            role = Role.Button
                            contentDescription = "Clear amount"
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        ) {
                            Icon(
                                imageVector = SolarIcons.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One-tap horizontal chips for category selection.
 */
@Composable
private fun QuickCategorySelector(
    categories: List<String>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onOpenAllCategories: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Icon(
                    imageVector = SolarIcons.Receipt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (categories.isNotEmpty()) {
                Text(
                    text = "See all (${categories.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button, onClick = onOpenAllCategories)
                        .padding(horizontal = Spacing.xs, vertical = 2.dp)
                )
            }
        }

        if (categories.isEmpty()) {
            SetupHint(
                text = "No categories yet. Connect Wallet in Settings to sync them.",
                actionLabel = "Open Settings",
                onAction = onOpenSettings
            )
        } else {
            // Prioritize the currently selected category at the front if not in top list
            val displayCategories = remember(categories, selectedCategory) {
                val top = categories.take(6).toMutableList()
                if (selectedCategory.isNotEmpty() && !top.contains(selectedCategory)) {
                    top.add(0, selectedCategory)
                }
                top
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                displayCategories.forEach { cat ->
                    val isSelected = cat == selectedCategory
                    FilterPill(
                        label = cat,
                        isSelected = isSelected,
                        onClick = { onSelectCategory(if (isSelected) "" else cat) }
                    )
                }

                if (categories.size > displayCategories.size) {
                    Surface(
                        onClick = onOpenAllCategories,
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        ) {
                            Icon(
                                imageVector = SolarIcons.SlidersHorizontal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "More...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One-tap horizontal chips for wallet account selection.
 */
@Composable
private fun QuickAccountSelector(
    accounts: List<String>,
    selectedAccount: String,
    accountError: String?,
    onSelectAccount: (String) -> Unit,
    onOpenAllAccounts: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Icon(
                imageVector = SolarIcons.Wallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Paid with",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (accounts.isEmpty()) {
            // Informational, not an error: the user has not done anything wrong, they simply
            // have not connected Wallet yet. Error colouring here made a first run look broken,
            // and red is also the one colour the form needs left free for real validation.
            SetupHint(
                text = "No accounts yet. Connect Wallet in Settings to choose where this goes.",
                actionLabel = "Open Settings",
                onAction = onOpenSettings
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                accounts.take(5).forEach { acc ->
                    val isSelected = acc == selectedAccount
                    FilterPill(
                        label = acc,
                        isSelected = isSelected,
                        onClick = { onSelectAccount(acc) }
                    )
                }

                if (accounts.size > 5) {
                    Surface(
                        onClick = onOpenAllAccounts,
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Text(
                            text = "More...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        )
                    }
                }
            }
        }

        accountError?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Compact, unified card for optional merchant and note details.
 */
@Composable
private fun DetailsCard(
    merchant: String,
    note: String,
    onMerchantChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "Details",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = merchant,
                onValueChange = onMerchantChange,
                label = { Text("Merchant / Payee") },
                placeholder = { Text("e.g. Grocery, Coffee, Uber") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                label = { Text("Note") },
                placeholder = { Text("Optional memo") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    }
}

/**
 * Modern tactile filter pill chip with high-contrast active state.
 */
@Composable
private fun FilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
        },
        label = "pill_bg"
    )
    val border by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        },
        label = "pill_border"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "pill_text"
    )

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = bg,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, border),
        modifier = modifier.semantics {
            role = Role.Checkbox
            this.selected = isSelected
            stateDescription = if (isSelected) "Selected" else "Not selected"
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            if (isSelected) {
                Icon(
                    imageVector = SolarIcons.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(end = 2.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Compact top-bar toggle pill between Expense and Income with fluid spring animation.
 */
@Composable
private fun TypeToggle(
    direction: TransactionDirection,
    onDirectionChange: (TransactionDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isExpense = direction == TransactionDirection.EXPENSE
    val extendedColors = Sms2WalletTheme.extendedColors

    // Smooth physics-based sliding thumb animation
    val targetFraction = if (isExpense) 0f else 1f
    val indicatorOffset by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "type_toggle_offset"
    )

    val thumbTint by animateColorAsState(
        targetValue = if (isExpense) {
            extendedColors.expense.copy(alpha = 0.10f)
        } else {
            extendedColors.income.copy(alpha = 0.12f)
        },
        label = "type_toggle_thumb_tint"
    )

    val thumbBorder by animateColorAsState(
        targetValue = if (isExpense) {
            extendedColors.expense.copy(alpha = 0.28f)
        } else {
            extendedColors.income.copy(alpha = 0.28f)
        },
        label = "type_toggle_thumb_border"
    )

    Box(
        modifier = modifier
            .padding(end = Spacing.xs)
            .width(184.dp)
            .height(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = CircleShape
            )
            .padding(3.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val segmentWidth = maxWidth / 2

            // Sliding Elevated Thumb
            Box(
                modifier = Modifier
                    .offset(x = segmentWidth * indicatorOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .shadow(elevation = 2.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .background(thumbTint)
                    .border(1.dp, thumbBorder, CircleShape)
            )

            // Segments Row
            Row(modifier = Modifier.fillMaxSize()) {
                val expenseContentColor by animateColorAsState(
                    targetValue = if (isExpense) extendedColors.expense else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    label = "expense_color"
                )

                // Expense Segment
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!isExpense) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDirectionChange(TransactionDirection.EXPENSE)
                            }
                        }
                        .semantics {
                            role = Role.RadioButton
                            this.selected = isExpense
                            contentDescription = "Switch to Expense"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = SolarIcons.ArrowUpRight,
                            contentDescription = null,
                            tint = expenseContentColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Expense",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isExpense) FontWeight.Bold else FontWeight.Medium,
                            color = expenseContentColor
                        )
                    }
                }

                val incomeContentColor by animateColorAsState(
                    targetValue = if (!isExpense) extendedColors.income else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    label = "income_color"
                )

                // Income Segment
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (isExpense) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDirectionChange(TransactionDirection.INCOME)
                            }
                        }
                        .semantics {
                            role = Role.RadioButton
                            this.selected = !isExpense
                            contentDescription = "Switch to Income"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = SolarIcons.ArrowDownLeft,
                            contentDescription = null,
                            tint = incomeContentColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Income",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (!isExpense) FontWeight.Bold else FontWeight.Medium,
                            color = incomeContentColor
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Light Mode", showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(name = "Dark Mode", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun AddCashExpensePreview() {
    Sms2WalletTheme {
        AddCashExpenseContent(
            state = TransactionDetailUiState(
                amountText = "250",
                category = "Groceries",
                accountName = "bKash",
                availableCategories = listOf("Groceries", "Dining", "Transport", "Bills", "Shopping", "Entertainment"),
                availableAccounts = listOf("bKash", "Cash", "City Bank")
            ),
            onBack = {},
            onSave = {},
            onAmountChange = {},
            onDirectionChange = {},
            onCategoryChange = {},
            onAccountChange = {},
            onMerchantChange = {},
            onNoteChange = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "TypeToggle - Light", showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(name = "TypeToggle - Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun TypeTogglePreview() {
    Sms2WalletTheme {
        var direction by remember { mutableStateOf(TransactionDirection.EXPENSE) }
        Surface(modifier = Modifier.padding(16.dp)) {
            TypeToggle(
                direction = direction,
                onDirectionChange = { direction = it }
            )
        }
    }
}

/**
 * A setup condition the user can act on, shown where a picker's options would be.
 *
 * Deliberately not error-coloured: nothing has gone wrong, the app just has nothing to offer
 * yet. It carries the fix as a button rather than only naming it in prose, so the user does not
 * have to leave, hunt through Settings and come back to find out which screen was meant.
 */
@Composable
private fun SetupHint(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xxs)
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
