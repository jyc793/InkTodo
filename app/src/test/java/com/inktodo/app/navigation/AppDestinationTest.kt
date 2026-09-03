package com.inktodo.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDestinationTest {
    @Test
    fun bottomNavigationContainsExactlyTheFourProductDestinations() {
        assertEquals(
            listOf(
                AppDestination.Todo,
                AppDestination.Calendar,
                AppDestination.Pomodoro,
                AppDestination.Completed,
            ),
            AppDestination.bottomDestinations,
        )
    }

    @Test
    fun allRoutesAreUnique() {
        val routes = AppDestination.entries.map(AppDestination::route)
        assertEquals(routes.size, routes.toSet().size)
        assertTrue(routes.none(String::isBlank))
    }

    @Test
    fun existingTaskRouteContainsItsStableDatabaseId() {
        assertEquals("task/edit/42", AppDestination.editTaskRoute(42))
        assertEquals("task/edit/{taskId}", AppDestination.EDIT_TASK_PATTERN)
    }
}
