package cn.hautpocket.app.domain

import org.junit.Assert.*
import org.junit.Test

class CourseBlocksTest {
    private fun course(sections: List<Int>) = Course(name = "高数", teacher = "张老师", location = "教室1", day = 1, weeks = listOf(1, 2), sections = sections)
    @Test fun mergesAdjacentRecordsAndSplitsGapsWithoutChangingSource() {
        val original = listOf(course(listOf(1, 2)), course(listOf(3)), course(listOf(5)))
        assertEquals(listOf(listOf(1, 2, 3), listOf(5)), courseBlocks(original, 1).map { it.sections })
        assertEquals(listOf(1, 2), original.first().sections)
        assertEquals(listOf(listOf(1, 2), listOf(5)), courseBlocks(listOf(course(listOf(5, 2, 1, 2))), 1).map { it.sections })
    }
    @Test fun preservesDifferentTeacherRoomCampusAndWeek() {
        val a = course(listOf(1))
        val b = course(listOf(2))
        for (other in listOf(b.copy(teacher = "李老师"), b.copy(location = "教室2"), b.copy(campus = Campus.SONGSHAN), b.copy(day = 2))) {
            assertEquals(2, courseBlocks(listOf(a, other), 1).size)
        }
        assertEquals(listOf(1), courseBlocks(listOf(a, b.copy(weeks = listOf(2))), 1).single().sections)
    }
    @Test fun preservesConflictsAsSeparateBlocks() {
        val list = courseBlocks(listOf(course(listOf(1, 2)), course(listOf(2, 3)).copy(name = "英语")), 1)
        assertEquals(2, list.size)
        assertEquals(listOf(listOf(1, 2), listOf(2, 3)), list.map { it.sections })
    }
}
