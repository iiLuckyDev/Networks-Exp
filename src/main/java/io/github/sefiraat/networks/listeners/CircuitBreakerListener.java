package io.github.sefiraat.networks.listeners;

import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.slimefun.network.NetworkCircuitBreaker;
import io.github.sefiraat.networks.slimefun.network.NetworkController;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

import javax.annotation.Nonnull;

public class CircuitBreakerListener implements Listener {

    private static final BlockFace[] CHECK_FACES = {
            BlockFace.SELF,
            BlockFace.UP,
            BlockFace.DOWN,
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST
    };

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRedstoneChange(@Nonnull BlockRedstoneEvent event) {
        for (BlockFace face : CHECK_FACES) {
            final Block block = face == BlockFace.SELF ? event.getBlock() : event.getBlock().getRelative(face);

            if (BlockStorage.check(block) instanceof NetworkCircuitBreaker
                    && NetworkCircuitBreaker.updatePoweredState(block.getLocation())) {
                invalidateNetwork(block.getLocation());
            }
        }
    }

    private void invalidateNetwork(@Nonnull Location location) {
        final NodeDefinition definition = NetworkStorage.getAllNetworkObjects().get(location);

        final NetworkRoot root = definition == null ? null : definition.getRoot();

        if (root == null) {
            return;
        }

        final Location controller = root.getController();

        if (controller != null) {
            NetworkController.getNetworks().remove(controller);
        }

        for (Location nodeLocation : root.getNodeLocations()) {
            final NodeDefinition nodeDefinition = NetworkStorage.getAllNetworkObjects().get(nodeLocation);

            if (nodeDefinition != null && nodeDefinition.getRoot() == root) {
                nodeDefinition.setNode(null);
            }
        }
    }
}
