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

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.api.Game;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.entity.DisplaceEntityEvent;
import org.spongepowered.api.event.game.state.GameStartingServerEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.network.ChannelBinding;
import org.spongepowered.api.network.ChannelBuf;
import org.spongepowered.api.network.RemoteConnection;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.world.World;

import java.io.*;
import java.nio.channels.Channels;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Plugin(
        id = "replayrestrictions",
        name = "Replay Restrictions",
        version = "1.0-SNAPSHOT"
)
public class SpongePlugin {
    private static final String CHANNEL = "replaymod:restrict";

    private final Map<String, Object> global = new HashMap<>();
    private final Map<String, Map<String, Object>> worlds = new HashMap<>();

    @Inject
    private Game game;

    @Inject
    @DefaultConfig(sharedRoot = true)
    private File defaultConfig;

    private ChannelBinding.RawDataChannel channel;

    @Subscribe
    public void onServerStarted(GameStartingServerEvent event) throws IOException {
        channel = game.getChannelRegistrar().createRawChannel(this, CHANNEL);

        if (!defaultConfig.exists()) {
            InputStream in = getClass().getResourceAsStream("/config.conf");
            FileOutputStream out = new FileOutputStream(defaultConfig);
            out.getChannel().transferFrom(Channels.newChannel(in), 0, Long.MAX_VALUE);
            out.close();
            in.close();
        }

        ConfigurationNode config = HoconConfigurationLoader.builder().setFile(defaultConfig).build().load();
        global.putAll(loadRestrictions(config));
        ConfigurationNode worlds = config.getNode("worlds");
        if (!worlds.isVirtual()) {
            for (ConfigurationNode world : worlds.getChildrenList()) {
                Map<String, Object> worldRestrictions = new HashMap<>(global);
                worldRestrictions.putAll(loadRestrictions(world));
                this.worlds.put(world.getKey().toString(), worldRestrictions);
            }
        }
    }

    private Map<String, Object> loadRestrictions(ConfigurationNode config) {
        Map<String, Object> map = new HashMap<>();
        String[] flags = {"no_xray", "no_noclip", "only_first_person", "only_recording_player", "hide_coordinates"};
        for (String key : flags) {
            if (!config.getNode(key).isVirtual()) {
                map.put(key, config.getNode(key).getBoolean());
            }
        }
        return map;
    }

    @Listener(order = Order.POST)
    public void onPlayerJoin(ClientConnectionEvent.Join event) {
        sendRestrictions(event.getTargetEntity(), event.getTargetEntity().getWorld());
    }

    @Listener(order = Order.POST)
    public void onWorldChange(DisplaceEntityEvent.Teleport.TargetPlayer event) {
        sendRestrictions(event.getTargetEntity(), event.getToTransform().getExtent());
    }

    /*
        Not sure if this is handled by the DisplaceEvent -> TODO testing
    @Listener(order = Order.POST)
    public void onWorldChange(PlayerRespawnEvent event) {
        sendRestrictions(event.getUser(), event.getNewRespawnLocation().getExtent());
    }
     */

    private void sendRestrictions(Player player, World world) {
        Map<String, Object> restrictions;
        if (worlds.containsKey(world.getName())) {
            restrictions = worlds.get(world.getName());
        } else {
            restrictions = global;
        }

        channel.sendTo(player, (buf) -> {
            for (Map.Entry<String, Object> e : restrictions.entrySet()) {
                byte[] bytes = e.getKey().getBytes();
                buf.writeByte((byte) bytes.length);
                for (byte b : bytes) {
                    buf.writeByte(b);
                }
                if (e.getValue() instanceof Boolean) {
                    boolean active = (Boolean) e.getValue();
                    buf.writeByte((byte) (active ? 1 : 0));
                } else {
                    throw new IllegalArgumentException(e.getValue().toString());
                }
            }
        });
    }
}
