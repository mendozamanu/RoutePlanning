package com.example.routeplanning.mvp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CordobaServiceAreaTest {
    @Test
    fun includesCordobaUrbanCoordinates() {
        assertTrue(CordobaServiceArea.contains(Coordinate(37.8882, -4.7794)))
    }

    @Test
    fun excludesCoordinatesOutsideTheUrbanServiceArea() {
        assertFalse(CordobaServiceArea.contains(Coordinate(40.4168, -3.7038)))
    }
}
