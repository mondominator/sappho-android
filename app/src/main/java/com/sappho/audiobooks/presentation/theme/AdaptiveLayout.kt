package com.sappho.audiobooks.presentation.theme

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Local composition for WindowSizeClass to be provided at the app level
 */
val LocalWindowSizeClass = compositionLocalOf<WindowSizeClass?> { null }

/**
 * Screen size categories for adaptive layouts
 */
enum class ScreenSize {
    COMPACT,    // Phone portrait (<600dp)
    MEDIUM,     // Phone landscape, small tablets, foldables (600-840dp)
    EXPANDED    // Tablets (>840dp)
}

/**
 * Get the current screen size category based on window width
 */
@Composable
fun rememberScreenSize(): ScreenSize {
    val windowSizeClass = LocalWindowSizeClass.current

    return if (windowSizeClass != null) {
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> ScreenSize.COMPACT
            WindowWidthSizeClass.Medium -> ScreenSize.MEDIUM
            WindowWidthSizeClass.Expanded -> ScreenSize.EXPANDED
            else -> ScreenSize.COMPACT
        }
    } else {
        // Fallback to configuration-based detection
        val configuration = LocalConfiguration.current
        when {
            configuration.screenWidthDp < 600 -> ScreenSize.COMPACT
            configuration.screenWidthDp < 840 -> ScreenSize.MEDIUM
            else -> ScreenSize.EXPANDED
        }
    }
}

/**
 * Check if the current layout is in landscape orientation
 */
@Composable
fun isLandscape(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.screenWidthDp > configuration.screenHeightDp
}

/**
 * Adaptive grid columns based on screen size
 * Uses Adaptive cells with minimum size for responsive grids
 */
object AdaptiveGrid {
    /**
     * Grid cells for audiobook cards (covers)
     * Minimum size ensures cards aren't too small on any device
     */
    val audiobookGrid: GridCells
        @Composable
        get() {
            val screenSize = rememberScreenSize()
            return when (screenSize) {
                ScreenSize.COMPACT -> GridCells.Fixed(2)
                ScreenSize.MEDIUM -> GridCells.Adaptive(minSize = 140.dp)
                ScreenSize.EXPANDED -> GridCells.Adaptive(minSize = 160.dp)
            }
        }

    /**
     * Grid cells for library view (can show more items)
     */
    val libraryGrid: GridCells
        @Composable
        get() {
            val screenSize = rememberScreenSize()
            return when (screenSize) {
                ScreenSize.COMPACT -> GridCells.Fixed(3)
                ScreenSize.MEDIUM -> GridCells.Adaptive(minSize = 120.dp)
                ScreenSize.EXPANDED -> GridCells.Adaptive(minSize = 140.dp)
            }
        }

    /**
     * Grid cells for category cards (larger items)
     */
    val categoryGrid: GridCells
        @Composable
        get() {
            val screenSize = rememberScreenSize()
            return when (screenSize) {
                ScreenSize.COMPACT -> GridCells.Fixed(2)
                ScreenSize.MEDIUM -> GridCells.Fixed(3)
                ScreenSize.EXPANDED -> GridCells.Adaptive(minSize = 200.dp)
            }
        }

    /**
     * Get fixed column count for screens that need exact column numbers
     */
    @Composable
    fun getColumnCount(baseColumns: Int = 2): Int {
        val screenSize = rememberScreenSize()
        return when (screenSize) {
            ScreenSize.COMPACT -> baseColumns
            ScreenSize.MEDIUM -> baseColumns + 1
            ScreenSize.EXPANDED -> baseColumns + 2
        }
    }
}

/**
 * Adaptive spacing and padding values
 */
object AdaptiveSpacing {
    /**
     * Horizontal padding for screen content
     */
    val screenPadding: Dp
        @Composable
        get() {
            val screenSize = rememberScreenSize()
            return when (screenSize) {
                ScreenSize.COMPACT -> 16.dp
                ScreenSize.MEDIUM -> 24.dp
                ScreenSize.EXPANDED -> 32.dp
            }
        }

    /**
     * Grid item spacing
     */
    val gridSpacing: Dp
        @Composable
        get() {
            val screenSize = rememberScreenSize()
            return when (screenSize) {
                ScreenSize.COMPACT -> 8.dp
                ScreenSize.MEDIUM -> 12.dp
                ScreenSize.EXPANDED -> 16.dp
            }
        }

    /**
     * Section spacing (between content sections)
     */
    val sectionSpacing: Dp
        @Composable
        get() {
            val screenSize = rememberScreenSize()
            return when (screenSize) {
                ScreenSize.COMPACT -> 24.dp
                ScreenSize.MEDIUM -> 32.dp
                ScreenSize.EXPANDED -> 40.dp
            }
        }
}

/**
 * Adaptive card sizes for horizontal scrolling sections
 */
object AdaptiveCardSize {
    /**
     * Large card size (e.g., Continue Listening)
     */
    val large: Dp
        @Composable
        get() {
            val screenSize = rememberScreenSize()
            return when (screenSize) {
                ScreenSize.COMPACT -> 160.dp
                ScreenSize.MEDIUM -> 180.dp
                ScreenSize.EXPANDED -> 200.dp
            }
        }

    /**
     * Standard card size (e.g., Recently Added)
     */
    val standard: Dp
        @Composable
        get() {
            val screenSize = rememberScreenSize()
            return when (screenSize) {
                ScreenSize.COMPACT -> 130.dp
                ScreenSize.MEDIUM -> 150.dp
                ScreenSize.EXPANDED -> 170.dp
            }
        }

    /**
     * Small card size (e.g., Listen Again)
     */
    val small: Dp
        @Composable
        get() {
            val screenSize = rememberScreenSize()
            return when (screenSize) {
                ScreenSize.COMPACT -> 110.dp
                ScreenSize.MEDIUM -> 130.dp
                ScreenSize.EXPANDED -> 150.dp
            }
        }
}

/**
 * Determine if navigation rail should be shown instead of top bar
 */
@Composable
fun shouldShowNavigationRail(): Boolean {
    val screenSize = rememberScreenSize()
    return screenSize == ScreenSize.EXPANDED ||
           (screenSize == ScreenSize.MEDIUM && isLandscape())
}

/**
 * Determine if detail screen should use two-column layout
 */
@Composable
fun shouldUseTwoColumnDetail(): Boolean {
    val screenSize = rememberScreenSize()
    return screenSize == ScreenSize.EXPANDED ||
           (screenSize == ScreenSize.MEDIUM && isLandscape())
}
