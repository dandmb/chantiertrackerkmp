package com.dmb.chantiertracker.presentation.auth.components

import com.dmb.chantiertracker.resources.action_back
import com.dmb.chantiertracker.presentation.theme.Ink
import com.dmb.chantiertracker.presentation.branding.ConstructionIllustration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextDecoration
import com.dmb.chantiertracker.presentation.AppOutlinedButton
import com.dmb.chantiertracker.presentation.AppOutlinedTextField
import com.dmb.chantiertracker.presentation.CapitalTitle
import com.dmb.chantiertracker.presentation.i18n.retryInLabel
import com.dmb.chantiertracker.presentation.main.ChevronRightIcon
import com.dmb.chantiertracker.presentation.theme.DisabledFill
import com.dmb.chantiertracker.presentation.theme.DisabledText
import com.dmb.chantiertracker.presentation.theme.Lemon
import com.dmb.chantiertracker.presentation.theme.Orange
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.field_code_label
import com.dmb.chantiertracker.resources.field_email_label
import com.dmb.chantiertracker.resources.field_name_label
import com.dmb.chantiertracker.resources.field_password_label
import com.dmb.chantiertracker.resources.password_hide
import com.dmb.chantiertracker.resources.password_show
import org.jetbrains.compose.resources.stringResource

const val FORM_ERROR_SNACKBAR_TAG = "form-error-snackbar"

private val CardCorner = 30.dp
private val CardOverlap = 28.dp

@Composable
fun AuthScreenLayout(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    centerContentVertically: Boolean = false,
    pinnedFooter: (@Composable ColumnScope.() -> Unit)? = null,
    errorMessage: String? = null,
    submitting: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    BoxWithConstraints(modifier.fillMaxSize()) {
        // The 228dp floor below reads fine against a normal portrait height
        // (700dp+) but was applied unconditionally — on a landscape phone
        // (~400dp tall) it alone ate over half the screen, cropping the
        // illustration and leaving the card barely enough room for a title.
        // Confirmed by rendering at 892x412 before and after this change.
        // Short on height is exactly the situation the keyboard-visible case
        // already handles (hide the illustration, collapse to a slim band) —
        // reused rather than inventing a second treatment.
        val shortOnHeight = maxHeight < 500.dp
        val showIllustration = !keyboardVisible && !shortOnHeight
        // Landscape has no room for a pinned block: it scrolls with the content instead.
        val footerPinned = pinnedFooter != null && !shortOnHeight
        val headerHeight by animateDpAsState(
            targetValue = if (keyboardVisible || shortOnHeight) 128.dp else if (footerPinned) (maxHeight * 0.2f).coerceIn(128.dp, 200.dp) else (maxHeight * 0.38f).coerceIn(228.dp, 360.dp),
            label = "authHeaderHeight",
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(Lemon),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(bottom = CardOverlap + 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(showIllustration) {
                    ConstructionIllustration(Modifier.widthIn(max = 260.dp).fillMaxWidth(0.62f))
                }
            }

            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(4.dp),
                ) {
                    Icon(
                        imageVector = BackArrowIcon,
                        contentDescription = stringResource(Res.string.action_back),
                        tint = Ink,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = (headerHeight - CardOverlap).coerceAtLeast(0.dp))
                    .fillMaxHeight(),
                shape = RoundedCornerShape(topStart = CardCorner, topEnd = CardCorner),
                color = MaterialTheme.colorScheme.surface,
            ) {
                if (centerContentVertically) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp)
                            .padding(top = 32.dp, bottom = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(
                            Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            AuthTitleBlock(title, subtitle)
                        }
                        Box(
                            Modifier.widthIn(max = 440.dp).fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            // Scrollable as a safety net, not the normal case: this
                            // content is short enough to just sit centered on any
                            // height this app actually ships to (confirmed at
                            // 892x412, the shortest real one) — but centering with
                            // no way to reach an overflow would strand content
                            // entirely on anything shorter still, unlike the
                            // non-centered branch below, which already scrolls.
                            Column(
                                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                content()
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .imePadding()
                                .then(if (footerPinned) Modifier else Modifier.navigationBarsPadding())
                                .padding(horizontal = 24.dp)
                                .padding(top = 32.dp, bottom = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Column(
                                Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                AuthTitleBlock(title, subtitle)
                                Spacer(Modifier.height(6.dp))
                                content()
                                if (!footerPinned) pinnedFooter?.invoke(this)
                            }
                        }
                        if (footerPinned) {
                            Column(
                                Modifier
                                    .widthIn(max = 440.dp)
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                content = pinnedFooter!!,
                            )
                        }
                    }
                }
            }
            FormErrorSnackbarHost(errorMessage, submitting, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
fun FormWithErrorSnackbar(
    errorMessage: String?,
    submitting: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        content()
        FormErrorSnackbarHost(errorMessage, submitting, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun FormErrorSnackbarHost(message: String?, submitting: Boolean, modifier: Modifier = Modifier) {
    val hostState = remember { SnackbarHostState() }
    LaunchedEffect(message, submitting) {
        hostState.currentSnackbarData?.dismiss()
        if (message != null && !submitting) {
            hostState.showSnackbar(message, withDismissAction = true, duration = SnackbarDuration.Long)
        }
    }
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .testTag(FORM_ERROR_SNACKBAR_TAG)
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) { data ->
        AppSnackbar(data)
    }
}

@Composable
fun AppSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Snackbar(
        snackbarData = data,
        modifier = modifier.drawBehind { drawRect(Orange, size = size.copy(width = 6.dp.toPx())) },
        shape = MaterialTheme.shapes.medium,
        containerColor = colors.inverseSurface,
        contentColor = colors.inverseOnSurface,
        actionColor = Lemon,
        actionContentColor = Lemon,
        dismissActionContentColor = Lemon,
    )
}

@Composable
private fun ColumnScope.AuthTitleBlock(title: String, subtitle: String?) {
    CapitalTitle(
        text = title,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    if (subtitle != null) {
        Text(
            text = subtitle,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
) {
    AppOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(Res.string.field_email_label)) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = imeAction),
    )
}

@Composable
fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
) {
    AppOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(Res.string.field_name_label)) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    )
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    error: String? = null,
    hint: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
) {
    KeepWindowPrivate()
    var visible by remember { mutableStateOf(false) }
    val supporting = error ?: hint
    val resolvedLabel = label ?: stringResource(Res.string.field_password_label)
    val clipboard = LocalClipboard.current
    val pasteOnlyClipboard = remember(clipboard) { PasteOnlyClipboard(clipboard) }
    CompositionLocalProvider(LocalClipboard provides pasteOnlyClipboard) {
        AppOutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth(),
            label = { Text(resolvedLabel) },
            singleLine = true,
            enabled = enabled,
            isError = error != null,
            supportingText = supporting?.let { { Text(it) } },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) VisibilityOffIcon else VisibilityIcon,
                        contentDescription = stringResource(if (visible) Res.string.password_hide else Res.string.password_show),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

private class PasteOnlyClipboard(private val clipboard: Clipboard) : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? = clipboard.getClipEntry()
    override suspend fun setClipEntry(clipEntry: ClipEntry?) = Unit
    override val nativeClipboard: NativeClipboard get() = clipboard.nativeClipboard
}

@Composable
fun CodeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
) {
    KeepWindowPrivate()
    AppOutlinedTextField(
        value = value,
        onValueChange = { input -> if (input.length <= 6 && input.all(Char::isDigit)) onValueChange(input) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(Res.string.field_code_label)) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
    )
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun InfoBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private val AuthButtonShape = RoundedCornerShape(percent = 50)

@Composable
fun AuthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true,
    waitSecondsLeft: Int = 0,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(54.dp),
        enabled = enabled && !loading && waitSecondsLeft == 0,
        shape = AuthButtonShape,
        colors = ButtonDefaults.buttonColors(disabledContainerColor = DisabledFill, disabledContentColor = DisabledText),
    ) {
        if (waitSecondsLeft > 0) {
            Text(retryInLabel(waitSecondsLeft), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp))
        } else if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp))
        }
    }
}

@Composable
fun AuthSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    AppOutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(54.dp),
        enabled = enabled,
        shape = AuthButtonShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp))
    }
}

@Composable
fun AuthLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), textDecoration = TextDecoration.Underline)
    }
}

@Composable
fun AuthInlineLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        textDecoration = TextDecoration.Underline,
    )
}

@Composable
fun AuthFooterPrompt(
    prompt: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.End,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$prompt ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = action,
            modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// A tappable, filled promo block for a secondary action that still deserves
// real visual weight (e.g. WelcomeScreen's entry into plan selection) —
// deliberately not just another AuthLink: primaryContainer fill + a chevron
// give it the same affordance as a button, without competing with the
// screen's actual primary/secondary buttons for top billing.
@Composable
fun AuthHighlightCard(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Icon(
                imageVector = ChevronRightIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
