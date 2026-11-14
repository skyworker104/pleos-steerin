package com.steerin.parkingmaster.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.steerin.parkingmaster.R
import com.steerin.parkingmaster.domain.reward.RewardProgress
import com.steerin.parkingmaster.domain.reward.RewardRules
import com.steerin.parkingmaster.domain.reward.WheelSkin
import com.steerin.parkingmaster.presentation.ui.theme.SteerinColors

/** Skin picker. Reachable only from standby (P); a manoeuvre starting pulls the UI back to MAIN. */
@Composable
fun SkinsScreen(progress: RewardProgress, selected: WheelSkin, onSelect: (WheelSkin) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.skins_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.skins_subtitle), style = MaterialTheme.typography.labelLarge, color = SteerinColors.Muted)
            }
            TextButton(onClick = onBack) { Text(stringResource(R.string.skins_back)) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WheelSkin.entries.forEach { skin ->
                SkinCard(skin, progress, skin == selected, Modifier.weight(1f)) { onSelect(skin) }
            }
        }
    }
}

@Composable
private fun SkinCard(skin: WheelSkin, progress: RewardProgress, inUse: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    val unlocked = skin in progress.unlocked
    val (cur, target) = RewardRules.requirement(skin, progress)
    val border = if (inUse) SteerinColors.Green else SteerinColors.Outline
    Column(
        modifier.background(SteerinColors.Surface, RoundedCornerShape(16.dp)).border(2.dp, border, RoundedCornerShape(16.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f).alpha(if (unlocked) 1f else 0.3f), contentAlignment = Alignment.Center) {
            GhostRing(liveDeg = 0f, targetDeg = 0f, snapped = false, toleranceDeg = 5f, showGhost = false, skin = skin, decorate = true)
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(skinName(skin)), style = MaterialTheme.typography.titleMedium, color = if (unlocked) SteerinColors.Text else SteerinColors.Muted)
        Text(stringResource(skinRequirement(skin)), style = MaterialTheme.typography.labelMedium, color = SteerinColors.Muted, textAlign = TextAlign.Center)
        if (!unlocked && target > 0) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).background(SteerinColors.SurfaceHigh, RoundedCornerShape(3.dp))) {
                Box(Modifier.fillMaxWidth((cur.toFloat() / target).coerceIn(0.03f, 1f)).height(6.dp).background(SteerinColors.Amber, RoundedCornerShape(3.dp)))
            }
            Text(stringResource(R.string.skin_progress, cur, target), style = MaterialTheme.typography.labelMedium, color = SteerinColors.Amber)
        }
        Spacer(Modifier.height(6.dp))
        when {
            inUse -> Text(stringResource(R.string.skin_in_use), style = MaterialTheme.typography.labelLarge, color = SteerinColors.Green, modifier = Modifier.padding(vertical = 10.dp))
            unlocked -> Button(onClick = onSelect, colors = ButtonDefaults.buttonColors(containerColor = SteerinColors.Cyan, contentColor = SteerinColors.Background)) {
                Text(stringResource(R.string.skin_select))
            }
            else -> Text(stringResource(R.string.skin_locked), style = MaterialTheme.typography.labelLarge, color = SteerinColors.Muted, modifier = Modifier.padding(vertical = 10.dp))
        }
    }
}

fun skinName(s: WheelSkin): Int = when (s) {
    WheelSkin.CLASSIC -> R.string.skin_classic
    WheelSkin.MASTER_RING -> R.string.skin_master_ring
    WheelSkin.CARBON_GRIP -> R.string.skin_carbon_grip
    WheelSkin.NEON_STREAK -> R.string.skin_neon_streak
    WheelSkin.GOLD_KING -> R.string.skin_gold_king
}

fun skinRequirement(s: WheelSkin): Int = when (s) {
    WheelSkin.CLASSIC -> R.string.skin_req_classic
    WheelSkin.MASTER_RING -> R.string.skin_req_master_ring
    WheelSkin.CARBON_GRIP -> R.string.skin_req_carbon_grip
    WheelSkin.NEON_STREAK -> R.string.skin_req_neon_streak
    WheelSkin.GOLD_KING -> R.string.skin_req_gold_king
}
