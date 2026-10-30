package cn.hautpocket.app.domain

/** Display-only merging: never changes imported course records or fills gaps. */
fun courseBlocks(courses: List<Course>, week: Int): List<Course> = courses.filter { week in it.weeks }
    .groupBy { listOf(it.day, it.name.trim(), it.teacher.trim(), it.location.trim(), it.campus, it.note) }
    .values.flatMap { group ->
        val runs = mutableListOf<MutableList<Int>>()
        group.flatMap { it.sections }.distinct().sorted().forEach { section ->
            if (runs.lastOrNull()?.last()?.plus(1) == section) runs.last().add(section)
            else runs.add(mutableListOf(section))
        }
        runs.map { run -> group.first().copy(id = "${group.first().id}-${run.first()}", sections = run,
            weeks = listOf(week)) }
    }.sortedWith(compareBy({ it.day }, { it.sections.first() }, { it.name }))
