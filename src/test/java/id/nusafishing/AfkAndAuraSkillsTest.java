package id.nusafishing;

import org.junit.Test;
import static org.junit.Assert.*;

import id.nusafishing.fishing.afk.AfkFishingManager;

public class AfkAndAuraSkillsTest {

    @Test
    public void testPercentParsing() {
        assertEquals(0.20, AfkFishingManager.parsePercent("20%", 0.10), 0.0001);
        assertEquals(0.35, AfkFishingManager.parsePercent("35%", 0.10), 0.0001);
        assertEquals(0.50, AfkFishingManager.parsePercent("50", 0.10), 0.0001);
        assertEquals(0.25, AfkFishingManager.parsePercent(0.25, 0.10), 0.0001);
        assertEquals(0.10, AfkFishingManager.parsePercent(null, 0.10), 0.0001);
        assertEquals(0.10, AfkFishingManager.parsePercent("invalid_value", 0.10), 0.0001);
        assertEquals(0.0, AfkFishingManager.parsePercent("0%", 0.10), 0.0001);
        assertEquals(1.0, AfkFishingManager.parsePercent("100%", 0.10), 0.0001);
    }

    @Test
    public void testNerfedWaitTimeCalculation() {
        double speedNerf = 0.30;
        int baseMin = 100;
        int baseMax = 500;
        int nerfedMin = (int) Math.round(baseMin * (1.0 + speedNerf));
        int nerfedMax = (int) Math.round(baseMax * (1.0 + speedNerf));

        assertEquals(130, nerfedMin);
        assertEquals(650, nerfedMax);
        assertTrue(nerfedMin > baseMin);
        assertTrue(nerfedMax > baseMax);
    }

    @Test
    public void testAuraSkillsHookLoadsWithoutAuraSkillsInClasspath() throws Exception {
        ClassLoader isolatingClassLoader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("dev.aurelium.")) {
                    throw new ClassNotFoundException("Blocked AuraSkills class in test: " + name);
                }
                return super.loadClass(name, resolve);
            }
        };

        // AuraSkillsHook must load and link cleanly without referencing dev.aurelium.*
        Class<?> hookClass = Class.forName("id.nusafishing.integration.auraskills.AuraSkillsHook", true, isolatingClassLoader);
        assertNotNull("AuraSkillsHook class should be loadable without AuraSkills on classpath", hookClass);
    }
}
