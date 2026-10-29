package id.nusafishing;

import id.nusafishing.commands.NusaFishingCommand;
import id.nusafishing.fishing.FishingListener;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class EconomyTransactionAuditTest {

    @Test
    public void testTabCompleteNoDuplicateSubcommands() {
        NusaFishingCommand cmd = new NusaFishingCommand(null, null);

        CommandSender mockSender = (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> {
                    if ("hasPermission".equals(method.getName())) {
                        return true;
                    }
                    return null;
                }
        );

        List<String> completions = cmd.onTabComplete(mockSender, null, "nusafishing", new String[]{""});
        assertNotNull(completions);

        Set<String> seen = new HashSet<>();
        for (String c : completions) {
            assertTrue("Duplicate subcommand found: " + c, seen.add(c));
        }
    }

    @Test
    public void testFishingListenerCleanupEventHandlers() throws Exception {
        Method deathHandler = FishingListener.class.getMethod("onPlayerDeath", PlayerDeathEvent.class);
        assertNotNull(deathHandler);
        assertTrue(deathHandler.isAnnotationPresent(EventHandler.class));

        Method worldChangeHandler = FishingListener.class.getMethod("onPlayerChangedWorld", PlayerChangedWorldEvent.class);
        assertNotNull(worldChangeHandler);
        assertTrue(worldChangeHandler.isAnnotationPresent(EventHandler.class));
    }
}
