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

import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class BungeeCordPlugin extends Plugin implements Listener {
    private static final String CHANNEL = "replaymod:restrict";

    private final Map<String, Object> global = new HashMap<String, Object>();
    private final Map<String, Map<String, Object>> servers = new HashMap<String, Map<String, Object>>();

    @Override
    public void onEnable() {
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().registerChannel(CHANNEL);

        try {
            if (!getDataFolder().exists()) {
                Files.createDirectory(getDataFolder().toPath());
            }

            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                Files.copy(getResourceAsStream("config.yml"), configFile.toPath());
            }
            Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
            global.putAll(loadRestrictions(config));
            if (!config.getSection("servers").getKeys().isEmpty()) {
                Configuration servers = config.getSection("servers");
                for (String server : servers.getKeys()) {
                    if (!servers.getSection(server).getKeys().isEmpty()) {
                        Map<String, Object> serverRestrictions = new HashMap<String, Object>(global);
                        serverRestrictions.putAll(loadRestrictions(servers.getSection(server)));
                        this.servers.put(server, serverRestrictions);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Map<String, Object> loadRestrictions(Configuration config) {
        Map<String, Object> map = new HashMap<String, Object>();
        String[] flags = {"no_xray", "no_noclip", "only_first_person", "only_recording_player", "hide_coordinates"};
        for (String key : flags) {
            if (config.get(key) instanceof Boolean) {
                map.put(key, config.get(key));
            }
        }
        return map;
    }

    @EventHandler(priority = 96)
    public void onPlayerJoin(ServerSwitchEvent event) {
        String serverName = event.getPlayer().getServer().getInfo().getName();
        Map<String, Object> restrictions;
        if (servers.containsKey(serverName)) {
            restrictions = servers.get(serverName);
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
            } catch (IOException ignored) {
            }
        }
        event.getPlayer().sendData(CHANNEL, out.toByteArray());
    }
}
