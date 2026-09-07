package com.chen.schedule.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chen.schedule.ui.course.CourseEditScreen
import com.chen.schedule.ui.import_.ImportScreen
import com.chen.schedule.ui.import_.ScraperLoginScreen
import com.chen.schedule.ui.schedule.ScheduleConfigScreen
import com.chen.schedule.ui.settings.SettingsScreen
import com.chen.schedule.ui.timetable.TimetableScreen

sealed class Screen(val route: String) {
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

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Timetable.route,
        modifier = Modifier.fillMaxSize()
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
                },
                onNavigateToImport = {
                    navController.navigate(Screen.Import.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Import.route) {
            ImportScreen(
                onScraperLogin = {
                    navController.navigate(Screen.ScraperLogin.route)
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToScheduleConfig = {
                    navController.navigate(Screen.ScheduleConfig.route)
                },
                onNavigateBack = { navController.popBackStack() }
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
