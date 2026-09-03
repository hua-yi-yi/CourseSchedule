package com.chen.schedule.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chen.schedule.ui.course.CourseEditScreen
import com.chen.schedule.ui.import_.ImportScreen
import com.chen.schedule.ui.import_.ScraperLoginScreen
import com.chen.schedule.ui.schedule.ScheduleConfigScreen
import com.chen.schedule.ui.settings.SettingsScreen
import com.chen.schedule.ui.timetable.TimetableScreen

sealed class Screen(val route: String, val label: String? = null) {
    object Timetable : Screen("timetable")
    object Import : Screen("import")
    object Settings : Screen("settings")
    object CourseEdit : Screen("course_edit?courseId={courseId}&semesterId={semesterId}") {
        fun createRoute(courseId: Long? = null, semesterId: Long) =
            "course_edit?courseId=${courseId ?: -1}&semesterId=$semesterId"
    }
    object ScheduleConfig : Screen("schedule_config")
    object ScraperLogin : Screen("scraper_login")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Timetable, "课程表", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    BottomNavItem(Screen.Import, "导入", Icons.Filled.FileDownload, Icons.Outlined.FileDownload),
    BottomNavItem(Screen.Settings, "设置", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            // Only show bottom bar on main screens
            val showBottomBar = bottomNavItems.any { item ->
                currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
            }
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.screen.route
                        } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Timetable.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Timetable.route) {
                TimetableScreen(
                    onAddCourse = { semesterId ->
                        navController.navigate(Screen.CourseEdit.createRoute(semesterId = semesterId))
                    },
                    onEditCourse = { courseId, semesterId ->
                        navController.navigate(Screen.CourseEdit.createRoute(courseId, semesterId))
                    },
                    onNavigateToScheduleConfig = {
                        navController.navigate(Screen.ScheduleConfig.route)
                    }
                )
            }

            composable(Screen.Import.route) {
                ImportScreen(
                    onScraperLogin = {
                        navController.navigate(Screen.ScraperLogin.route)
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToScheduleConfig = {
                        navController.navigate(Screen.ScheduleConfig.route)
                    }
                )
            }

            composable(
                route = Screen.CourseEdit.route,
                arguments = listOf(
                    navArgument("courseId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("semesterId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val courseId = backStackEntry.arguments?.getLong("courseId") ?: -1L
                val semesterId = backStackEntry.arguments?.getLong("semesterId") ?: 0L
                CourseEditScreen(
                    courseId = if (courseId == -1L) null else courseId,
                    semesterId = semesterId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ScheduleConfig.route) {
                ScheduleConfigScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ScraperLogin.route) {
                ScraperLoginScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
