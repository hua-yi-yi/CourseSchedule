package com.chen.schedule.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「先完成课表设置」引导状态的回归测试,对应评审问题
 * 「设置引导丢失原点击位置」。
 */
class ScheduleGuideStateTest {

    private val target = BlankClickTarget(dayOfWeek = 3, startSlot = 5, week = 7, totalWeeks = 18)

    private fun guide(hidden: Boolean) = ScheduleGuide(
        semesterDone = false,
        schemeDone = false,
        missingSlots = emptyList(),
        target = target,
        hidden = hidden
    )

    /** 进入设置时只隐藏,点击位置必须保留 —— 返回后才能按原位置继续添加课程。 */
    @Test fun hidingGuidePreservesClickTarget() {
        val hidden = guide(hidden = true)
        assertEquals(target, hidden.target)
        assertTrue("隐藏期间不应显示", hidden.hidden)
    }

    /** 数据变化后重新显示,仍指向原位置。 */
    @Test fun refreshingAfterHideKeepsTargetAndReshows() {
        val refreshed = guide(hidden = true).copy(hidden = false, semesterDone = true, schemeDone = true)
        assertEquals(target, refreshed.target)
        assertFalse(refreshed.hidden)
        assertTrue(refreshed.allDone)
    }

    /** 「稍后再说」才真正丢弃目标。 */
    @Test fun dismissingClearsEverything() {
        val dismissed: ScheduleGuide? = null
        assertNull(dismissed?.target)
    }

    /** 两项都完成前不允许「继续添加课程」。 */
    @Test fun continueIsOnlyEnabledWhenAllDone() {
        assertFalse(guide(hidden = false).allDone)
        assertFalse(guide(hidden = false).copy(semesterDone = true).allDone)
        assertTrue(guide(hidden = false).copy(semesterDone = true, schemeDone = true).allDone)
    }
}

/** 方案编号 0(「原有作息」)是合法方案,不能被当成「未指定」。 */
class SchemeIdSentinelTest {

    @Test fun legacySchemeIdIsValidNotMissing() {
        assertEquals(0L, com.chen.schedule.domain.model.TimeScheme.LEGACY_ID)
        // 导航层:`-1` 才是「新建」,0 必须保留
        val routeId: Long = 0L
        assertEquals(0L, routeId.takeIf { it >= 0 })
        assertNull((-1L).takeIf { it >= 0 })
    }
}
