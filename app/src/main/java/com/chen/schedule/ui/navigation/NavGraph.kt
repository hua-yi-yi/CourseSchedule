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
import com.chen.schedule.ui.schedule.SchemeEditScreen
import com.chen.schedule.ui.schedule.SchemeListScreen
import com.chen.schedule.ui.schedule.SchemeMenuScreen
import com.chen.schedule.ui.schedule.SemesterEditScreen
import com.chen.schedule.ui.schedule.SemesterListScreen
import com.chen.schedule.ui.schedule.SemesterMenuScreen
import com.chen.schedule.ui.schedule.SetupWizardScreen
import com.chen.schedule.ui.settings.SettingsScreen
import com.chen.schedule.ui.timetable.BlankClickTarget
import com.chen.schedule.ui.timetable.TimetableScreen

sealed class Screen(val route: String) {
    object Timetable : Screen("timetable")
    object Import : Screen("import")
    object Settings : Screen("settings")

    object CourseEdit :
        Screen("course_edit?courseId={courseId}&semesterId={semesterId}&day={day}&slot={slot}&week={week}&totalWeeks={totalWeeks}") {
        fun createRoute(
            courseId: Long? = null,
            semesterId: Long,
            day: Int = -1,
            slot: Int = -1,
            week: Int = -1,
            totalWeeks: Int = 16
        ) = "course_edit?courseId=${courseId ?: -1}&semesterId=$semesterId" +
            "&day=$day&slot=$slot&week=$week&totalWeeks=$totalWeeks"
    }

    /** 学期与作息(两张卡片的总览页)。 */
    object ScheduleConfig : Screen("schedule_config")
    object SetupWizard : Screen("setup_wizard")
    object SemesterMenu : Screen("semester_menu")
    object SemesterList : Screen("semester_list")
    object SemesterEdit : Screen("semester_edit?semesterId={semesterId}") {
        fun createRoute(semesterId: Long? = null) = "semester_edit?semesterId=${semesterId ?: -1}"
    }
    object SchemeMenu : Screen("scheme_menu")
    object SchemeList : Screen("scheme_list")
    object SchemeEdit : Screen("scheme_edit?schemeId={schemeId}") {
        fun createRoute(schemeId: Long? = null) = "scheme_edit?schemeId=${schemeId ?: -1}"
    }

    object HaustImport : Screen("haust_import")
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
                onAddCourse = { semesterId, target: BlankClickTarget? ->
                    navController.navigate(
                        Screen.CourseEdit.createRoute(
                            semesterId = semesterId,
                            day = target?.dayOfWeek ?: -1,
                            slot = target?.startSlot ?: -1,
                            week = target?.week ?: -1,
                            totalWeeks = target?.totalWeeks ?: 16
                        )
                    )
                },
                onEditCourse = { courseId, semesterId ->
                    navController.navigate(Screen.CourseEdit.createRoute(courseId, semesterId))
                },
                onNavigateToScheduleConfig = {
                    navController.navigate(Screen.ScheduleConfig.route)
                },
                onNavigateToSetupWizard = {
                    navController.navigate(Screen.SetupWizard.route)
                },
                onNavigateToSemesterSettings = {
                    navController.navigate(Screen.SemesterMenu.route)
                },
                onNavigateToSchemeSettings = {
                    navController.navigate(Screen.SchemeMenu.route)
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
                onHaustImport = { navController.navigate(Screen.HaustImport.route) },
                onScraperLogin = { navController.navigate(Screen.ScraperLogin.route) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToScheduleConfig = { navController.navigate(Screen.ScheduleConfig.route) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.CourseEdit.route,
            arguments = listOf(
                navArgument("courseId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("semesterId") { type = NavType.LongType },
                navArgument("day") { type = NavType.IntType; defaultValue = -1 },
                navArgument("slot") { type = NavType.IntType; defaultValue = -1 },
                navArgument("week") { type = NavType.IntType; defaultValue = -1 },
                navArgument("totalWeeks") { type = NavType.IntType; defaultValue = 16 }
            )
        ) { backStackEntry ->
            val courseId = backStackEntry.arguments?.getLong("courseId") ?: -1L
            val semesterId = backStackEntry.arguments?.getLong("semesterId") ?: 0L
            val day = backStackEntry.arguments?.getInt("day") ?: -1
            val slot = backStackEntry.arguments?.getInt("slot") ?: -1
            val week = backStackEntry.arguments?.getInt("week") ?: -1
            val totalWeeks = backStackEntry.arguments?.getInt("totalWeeks") ?: 16
            CourseEditScreen(
                courseId = if (courseId == -1L) null else courseId,
                semesterId = semesterId,
                onNavigateBack = { navController.popBackStack() },
                prefillDay = day.takeIf { it > 0 },
                prefillSlot = slot.takeIf { it > 0 },
                prefillWeek = week.takeIf { it > 0 },
                prefillTotalWeeks = totalWeeks
            )
        }

        // ===== 学期与作息:总览 → 二级菜单 → 列表 / 编辑 =====
        composable(Screen.ScheduleConfig.route) {
            ScheduleConfigScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenSemesterMenu = { navController.navigate(Screen.SemesterMenu.route) },
                onOpenSchemeMenu = { navController.navigate(Screen.SchemeMenu.route) }
            )
        }

        // ===== 首启「初始设置」向导 =====
        composable(Screen.SetupWizard.route) {
            SetupWizardScreen(
                onDone = { navController.popBackStack() },
                onGoImport = {
                    navController.popBackStack()
                    navController.navigate(Screen.Import.route)
                },
                onGoHaustImport = {
                    navController.popBackStack()
                    navController.navigate(Screen.HaustImport.route)
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Screen.SemesterMenu.route) {
            SemesterMenuScreen(
                onNavigateBack = { navController.popBackStack() },
                onSelectExisting = { navController.navigate(Screen.SemesterList.route) },
                onCreateNew = { navController.navigate(Screen.SemesterEdit.createRoute(null)) },
                onEditCurrent = { id -> navController.navigate(Screen.SemesterEdit.createRoute(id)) }
            )
        }

        composable(Screen.SemesterList.route) {
            SemesterListScreen(
                onNavigateBack = { navController.popBackStack() },
                onEditSemester = { id -> navController.navigate(Screen.SemesterEdit.createRoute(id)) }
            )
        }

        composable(
            route = Screen.SemesterEdit.route,
            arguments = listOf(
                navArgument("semesterId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("semesterId") ?: -1L
            SemesterEditScreen(
                semesterId = id.takeIf { it > 0 },
                onDone = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Screen.SchemeMenu.route) {
            SchemeMenuScreen(
                onNavigateBack = { navController.popBackStack() },
                onSelectExisting = { navController.navigate(Screen.SchemeList.route) },
                onCreateNew = { navController.navigate(Screen.SchemeEdit.createRoute(null)) },
                // 0 = 「原有作息」,是合法方案编号;只有负值才表示「新建」
                onEditCurrent = { id -> navController.navigate(Screen.SchemeEdit.createRoute(id.takeIf { it >= 0 })) }
            )
        }

        composable(Screen.SchemeList.route) {
            SchemeListScreen(
                onNavigateBack = { navController.popBackStack() },
                onEditScheme = { id -> navController.navigate(Screen.SchemeEdit.createRoute(id)) }
            )
        }

        composable(
            route = Screen.SchemeEdit.route,
            arguments = listOf(
                navArgument("schemeId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("schemeId") ?: -1L
            SchemeEditScreen(
                schemeId = id.takeIf { it >= 0 },
                onDone = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Screen.HaustImport.route) {
            com.chen.schedule.ui.import_.HaustImportScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.ScraperLogin.route) {
            ScraperLoginScreen(
                onHaustImport = { navController.navigate(Screen.HaustImport.route) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
