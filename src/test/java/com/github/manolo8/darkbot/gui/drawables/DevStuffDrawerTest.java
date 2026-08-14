package com.github.manolo8.darkbot.gui.drawables;

import eu.darkbot.api.game.other.Area;
import eu.darkbot.api.game.other.Obstacle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DevStuffDrawerTest {

    @Test
    void overlayOnlyDrawsActiveNonEmptyObstacles() {
        Obstacle obstacle = mock(Obstacle.class);
        Area area = mock(Area.class);
        when(obstacle.isValid()).thenReturn(true);
        when(obstacle.use()).thenReturn(true);
        when(obstacle.getArea()).thenReturn(area);
        when(area.isEmpty()).thenReturn(false);

        assertTrue(DevStuffDrawer.shouldDrawObstacle(obstacle));

        when(obstacle.use()).thenReturn(false);
        assertFalse(DevStuffDrawer.shouldDrawObstacle(obstacle));

        when(obstacle.use()).thenReturn(true);
        when(area.isEmpty()).thenReturn(true);
        assertFalse(DevStuffDrawer.shouldDrawObstacle(obstacle));
    }
}
