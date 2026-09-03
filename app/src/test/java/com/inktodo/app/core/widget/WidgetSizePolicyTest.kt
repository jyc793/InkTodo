package com.inktodo.app.core.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSizePolicyTest {
    @Test
    fun commonHyperOsSizesIncreaseRealTaskCapacity() {
        assertEquals(1, WidgetSizePolicy.resolve(110, 110, 20).maxTaskRows)
        assertEquals(1, WidgetSizePolicy.resolve(250, 110, 20).maxTaskRows)
        assertEquals(2, WidgetSizePolicy.resolve(250, 180, 20).maxTaskRows)
        assertEquals(4, WidgetSizePolicy.resolve(250, 250, 20).maxTaskRows)
        assertEquals(6, WidgetSizePolicy.resolve(320, 330, 20).maxTaskRows)
    }

    @Test
    fun widthAndHeightControlSecondaryContentInsteadOfScalingTypography() {
        assertFalse(WidgetSizePolicy.resolve(110, 250, 20).showSecondaryText)
        assertFalse(WidgetSizePolicy.resolve(250, 110, 20).showSecondaryText)
        assertTrue(WidgetSizePolicy.resolve(250, 180, 20).showSecondaryText)
    }

    @Test
    fun persistedUserLimitCapsRowsWithinSafeRange() {
        assertEquals(1, WidgetSizePolicy.resolve(320, 330, 0).maxTaskRows)
        assertEquals(4, WidgetSizePolicy.resolve(320, 330, 4).maxTaskRows)
        assertEquals(6, WidgetSizePolicy.resolve(320, 330, 200).maxTaskRows)
    }
}
