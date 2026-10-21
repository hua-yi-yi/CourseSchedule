package com.chen.schedule.ui.theme

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shared compact header for every destination outside the timetable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) {
                title()
            }
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        expandedHeight = 48.dp,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.primary
        )
    )
}

/** Timetable card geometry and spacing, also used by forms and settings. */
object ScheduleStyle {
    val PagePadding = 12.dp
    val CardRadius = 12.dp
    val CardShape = androidx.compose.foundation.shape.RoundedCornerShape(CardRadius)
}
