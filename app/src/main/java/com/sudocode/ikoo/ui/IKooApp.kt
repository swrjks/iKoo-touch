package com.sudocode.ikoo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sudocode.ikoo.ui.components.PremiumCard
import com.sudocode.ikoo.ui.dashboard.DashboardScreen
import com.sudocode.ikoo.ui.gallery.GalleryScreen
import com.sudocode.ikoo.ui.history.HistoryScreen
import com.sudocode.ikoo.ui.home.HomeScreen
import com.sudocode.ikoo.ui.theme.ElectricMint
import com.sudocode.ikoo.ui.theme.Frost
import com.sudocode.ikoo.ui.theme.InkBlack
import com.sudocode.ikoo.ui.theme.SignalBlue
import com.sudocode.ikoo.ui.theme.SoftViolet

@Composable
fun IKooApp() {
    var selectedTab by remember { mutableStateOf(IKooTab.Home) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(InkBlack)
    ) {
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn() + slideInHorizontally { it * direction / 4 }) togetherWith
                    (fadeOut() + slideOutHorizontally { -it * direction / 4 })
            },
            label = "ikooTabContent"
        ) { tab ->
            when (tab) {
                IKooTab.Home -> HomeScreen(bottomPadding = 112.dp)
                IKooTab.Dashboard -> DashboardScreen()
                IKooTab.History -> HistoryScreen(bottomPadding = 112.dp)
                IKooTab.Gallery -> GalleryScreen(bottomPadding = 112.dp)
            }
        }

        BottomNavigationBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun BottomNavigationBar(
    selectedTab: IKooTab,
    onTabSelected: (IKooTab) -> Unit,
    modifier: Modifier = Modifier
) {
    PremiumCard(
        modifier = modifier.fillMaxWidth(0.94f),
        contentPadding = PaddingValues(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IKooTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (selected) {
                                Brush.horizontalGradient(listOf(ElectricMint, SignalBlue, SoftViolet))
                            } else {
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.07f),
                                        Color.White.copy(alpha = 0.03f)
                                    )
                                )
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTabSelected(tab) }
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) Color.White else Frost.copy(alpha = 0.82f)
                    )
                }
            }
        }
    }
}

private enum class IKooTab(val label: String) {
    Home("Home"),
    Dashboard("Live"),
    History("History"),
    Gallery("Memories")
}
