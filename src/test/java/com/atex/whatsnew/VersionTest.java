package com.atex.whatsnew;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VersionTest {

    @Test
    public void verify_strips_snapshot() {
        assertEquals("1.0.0", WhatsNewMojo.stripVersion("1.0.0-SNAPSHOT", false));
    }

    @Test
    public void verify_strips_fixversion() {
        assertEquals("1.0", WhatsNewMojo.stripVersion("1.0.0", true));
    }

    @Test
    public void verify_strips_snapshot_fixversion() {
        assertEquals("1.0", WhatsNewMojo.stripVersion("1.0.0-SNAPSHOT", true));
    }

    @Test
    public void verify_does_not_touch_odd_version() {
        assertEquals("ex.0.0", WhatsNewMojo.stripVersion("ex.0.0", true));
    }

    @Test
    public void verify_strip_snapshot_from_odd_version() {
        assertEquals("ex.0.0", WhatsNewMojo.stripVersion("ex.0.0-SNAPSHOT", true));
    }
}
