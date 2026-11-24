package vn.cgnn.admin.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object NfcColors {
    val BgStart = Color(0xFFEEF1F5)
    val BgMid = Color(0xFFFAFBFC)
    val BgEnd = Color(0xFFE5E9F0)

    val TextPrimary = Color(0xFF182230)
    val TextDeep = Color(0xFF0D2238)
    val TextMuted = Color(0xFF47607A)
    val TextSubtle = Color(0xFF64748B)

    val CardBorder = Color(0xFFFFFFFF)
    val CardDivider = Color(0xFFE2E6EC)
    val SurfaceSoft = Color(0xFFF2F4F7)
    val SurfaceMuted = Color(0xFFE5E8EC)

    val SelectedBorder = Color(0xFFF8C700)
    val SelectedBg = Color(0xFFFFFBE8)
    val GoldStart = Color(0xFFFFE27A)
    val GoldMid = Color(0xFFF8C700)
    val GoldEnd = Color(0xFFE0A800)
    val GoldText = Color(0xFF2A1D00)

    val DarkBarStart = Color(0xFF374151)
    val DarkBarEnd = Color(0xFF1F2937)

    val SilverBtnBorder = Color(0xFFFFFBF0)
    val SilverBtnStart = Color(0xFFFFFFFF)
    val SilverBtnMid = Color(0xFFE2D9C3)
    val SilverBtnEnd = Color(0xFFE7E1D2)
    val SilverBtnText = Color(0xFF292114)

    val GreenActive = Color(0xFF12936A)
    val GreenActiveText = Color(0xFF0A7A56)
    val GreenForest = Color(0xFF31543B)
    val CreamBg = Color(0xFFEFE6D3)
    val CreamBar = Color(0xFFE6DCCB)
    val CoralError = Color(0xFFE15F45)

    val AmberBg = Color(0xFFFFFBEB)
    val AmberBorder = Color(0xFFFDE68A)
    val AmberText = Color(0xFF92400E)

    val BlueBg = Color(0xFFEFF6FF)
    val BlueBorder = Color(0xFFBFDBFE)
    val BlueText = Color(0xFF1D4ED8)

    val EmeraldBg = Color(0xFFECFDF5)
    val EmeraldBorder = Color(0xFFA7F3D0)
    val EmeraldText = Color(0xFF047857)

    val DangerBg = Color(0xFFFEF2F2)
    val DangerBorder = Color(0xFFFECACA)
    val DangerText = Color(0xFFB91C1C)

    val PearlBackgroundBrush = Brush.verticalGradient(
        colors = listOf(BgStart, BgMid, BgEnd, Color(0xFFF7F8FA)),
    )

    val SilverPrimaryBrush = Brush.linearGradient(
        colors = listOf(SilverBtnStart, Color(0xFFF5F2EA), SilverBtnMid, Color(0xFFFFFDF8), SilverBtnEnd),
    )

    val GoldBannerBrush = Brush.linearGradient(
        colors = listOf(GoldStart, GoldMid, GoldEnd),
    )
}

private val AdminColorScheme = lightColorScheme(
    primary = NfcColors.DarkBarStart,
    onPrimary = Color.White,
    secondary = NfcColors.GreenForest,
    onSecondary = Color.White,
    background = NfcColors.BgStart,
    onBackground = NfcColors.TextPrimary,
    surface = Color.White,
    onSurface = NfcColors.TextPrimary,
    error = NfcColors.DangerText,
    onError = Color.White,
)

@Composable
fun CgnnAdminTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AdminColorScheme,
        content = content,
    )
}

@Composable
fun SilverPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeightDp: Int = 52,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = minHeightDp.dp)
            .clip(shape)
            .background(
                brush = if (enabled) NfcColors.SilverPrimaryBrush
                else Brush.linearGradient(listOf(Color(0xFFE5E7EB), Color(0xFFD1D5DB))),
            )
            .border(1.5.dp, if (enabled) Color(0xFFD6CBB2) else Color(0xFFCBD5E1), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            content = content,
        )
    }
}

@Composable
fun LineActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeightDp: Int = 50,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = minHeightDp.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, Color(0xFFC8CFD8)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = NfcColors.TextDeep,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        content = content,
    )
}

@Composable
fun SoftActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeightDp: Int = 50,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = minHeightDp.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NfcColors.DarkBarStart,
            contentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        content = content,
    )
}

@Composable
fun DangerActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeightDp: Int = 50,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = minHeightDp.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, NfcColors.DangerBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = NfcColors.DangerBg,
            contentColor = NfcColors.DangerText,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        content = content,
    )
}

@Composable
fun QuickCopyPill(
    onClick: () -> Unit,
    containerColor: Color,
    borderColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 40.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        content = content,
    )
}
