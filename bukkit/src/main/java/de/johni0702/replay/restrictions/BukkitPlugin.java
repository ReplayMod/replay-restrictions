/*
 * This file is part of ReplayRestrictions, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2015 johni0702 <https://github.com/johni0702>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.johni0702.replay.restrictions;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BukkitPlugin extends JavaPlugin implements Listener {
    private static final String CHANNEL = "replaymod:restrict";

    private final Map<String, Object> global = new HashMap<String, Object>();
    private final Map<String, Map<String, Object>> worlds = new HashMap<String, Map<String, Object>>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);

        saveDefaultConfig();

        global.putAll(loadRestrictions(getConfig()));
        if (getConfig().isConfigurationSection("worlds")) {
            ConfigurationSection worlds = getConfig().getConfigurationSection("worlds");
            for (String world : worlds.getKeys(false)) {
                if (worlds.isConfigurationSection(world)) {
                    Map<String, Object> worldRestrictions = new HashMap<String, Object>(global);
                    worldRestrictions.putAll(loadRestrictions(worlds.getConfigurationSection(world)));
                    this.worlds.put(world, worldRestrictions);
                }
            }
        }
    }

    private Map<String, Object> loadRestrictions(ConfigurationSection config) {
        Map<String, Object> map = new HashMap<String, Object>();
        String[] flags = {"no_xray", "no_noclip", "only_first_person", "only_recording_player", "hide_coordinates"};
        for (String key : flags) {
            if (config.isBoolean(key)) {
                map.put(key, config.get(key));
            }
        }
        return map;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRegister(PlayerRegisterChannelEvent event) {
        if (!event.getChannel().equals(CHANNEL)) return;
        sendRestrictions(event.getPlayer(), event.getPlayer().getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        sendRestrictions(event.getPlayer(), event.getPlayer().getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        sendRestrictions(event.getPlayer(), event.getPlayer().getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerRespawnEvent event) {
        sendRestrictions(event.getPlayer(), event.getRespawnLocation().getWorld());
    }

    private void sendRestrictions(Player player, World world) {
        Map<String, Object> restrictions;
        if (worlds.containsKey(world.getName())) {
            restrictions = worlds.get(world.getName());
        } else {
            restrictions = global;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Map.Entry<String, Object> e : restrictions.entrySet()) {
            try {
                byte[] bytes = e.getKey().getBytes();
                out.write(bytes.length);
                out.write(bytes);
                if (e.getValue() instanceof Boolean) {
                    boolean active = (Boolean) e.getValue();
                    out.write(active ? 1 : 0);
                } else {
                    throw new IllegalArgumentException(e.getValue().toString());
                }
            } catch (IOException ignored) {}
        }
        player.sendPluginMessage(this, CHANNEL, out.toByteArray());
    }
}
