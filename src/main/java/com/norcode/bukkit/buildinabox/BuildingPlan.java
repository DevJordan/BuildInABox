package com.norcode.bukkit.buildinabox;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import org.bukkit.material.Directional;
import org.bukkit.material.EnderChest;
import org.bukkit.metadata.FixedMetadataValue;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;

import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.MaxChangedBlocksException;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.data.DataException;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.schematic.SchematicFormat;

public class BuildingPlan {
    String name;
    String filename;
    List<String> description;
    BuildInABox plugin;
    
    private static final EnumSet<Material> coverableBlocks = EnumSet.of(Material.LONG_GRASS, Material.SNOW, Material.AIR, Material.RED_MUSHROOM, Material.BROWN_MUSHROOM, Material.DEAD_BUSH, Material.FIRE, Material.RED_ROSE, Material.YELLOW_FLOWER, Material.SAPLING);
    
    public BuildingPlan(BuildInABox plugin, String name, String filename, List<String> description) {
        this.plugin = plugin;
        this.name = name;
        this.filename = filename;
        this.description = description;
    }

    public String getName() {
        return this.name;
    }

    @SuppressWarnings("incomplete-switch")
    public static int getRotationDegrees(BlockFace from, BlockFace to) {
        switch (from) {
        case NORTH:
            switch (to) {
            case NORTH:
                return 0;
            case EAST:
                return 90;
            case SOUTH:
                return 180;
            case WEST:
                return 270;
            }
            break;
        case EAST:
            switch (to) {
            case NORTH:
                return 270;
            case EAST:
                return 0;
            case SOUTH:
                return 90;
            case WEST:
                return 180;
            }
            break;
        case SOUTH:
            switch (to) {
            case NORTH:
                return 180;
            case EAST:
                return 270;
            case SOUTH:
                return 0;
            case WEST:
                return 90;
            }
            break;
        case WEST:
            switch (to) {
            case NORTH:
                return 90;
            case EAST:
                return 180;
            case SOUTH:
                return 270;
            case WEST:
                return 0;
            }
            break;
        default:
            return 0;
        }
        return 0;
    }

    public static Vector findEnderChest(CuboidClipboard cc) {
        for (int x = 0; x < cc.getSize().getBlockX(); x++) {
            for (int y = 0; y < cc.getSize().getBlockY(); y++) {
                for (int z = 0; z < cc.getSize().getBlockZ(); z++) {
                    Vector v = new Vector(x,y,z);
                    if (cc.getPoint(v).getType() == BuildInABox.BLOCK_ID) {
                        return new Vector(-v.getBlockX(), -v.getBlockY(), -v.getBlockZ());
                    }
                }
            }
        }
        return null;
    }

    public static BuildingPlan fromClipboard(BuildInABox plugin, Player player, String name) {
        WorldEditPlugin we = plugin.getWorldEdit();
        BuildingPlan plan = null;
        LocalSession session = we.getSession(player);
        EditSession es = new EditSession(new BukkitWorld(player.getWorld()), we.getWorldEdit().getConfiguration().maxChangeLimit);
        es.enableQueue();
        CuboidClipboard cc = null;
        try {
            com.sk89q.worldedit.LocalPlayer wp = we.wrapPlayer(player);
            Region region = session.getSelection(wp.getWorld());
            Vector min = region.getMinimumPoint();
            Vector max = region.getMaximumPoint();
            Vector pos = session.getPlacementPosition(wp);

            cc = new CuboidClipboard(
                    max.subtract(min).add(new Vector(1, 1, 1)),
                    min, min.subtract(pos));
            cc.copy(es);
            es.flushQueue();
        } catch (IncompleteRegionException e) {
            player.sendMessage(BuildInABox.getErrorMsg("world-edit-selection-needed"));
            return null;
        }
        Vector chestOffset = findEnderChest(cc);
        if (chestOffset == null) {
            player.sendMessage(BuildInABox.getErrorMsg("enderchest-not-found"));
            return null;
        }
        
        Directional md = (Directional) Material.getMaterial(BuildInABox.BLOCK_ID).getNewData((byte)cc.getPoint(new Vector(-chestOffset.getBlockX(), -chestOffset.getBlockY(), -chestOffset.getBlockZ())).getData());
        if (!md.getFacing().equals(BlockFace.NORTH)) {
            cc.rotate2D(getRotationDegrees(md.getFacing(), BlockFace.NORTH));
            chestOffset = findEnderChest(cc);
        }
        cc.setOffset(chestOffset);
        try {
            SchematicFormat.MCEDIT.save(cc, new File(new File(plugin.getDataFolder(), "schematics"), name + ".schematic"));
            plan = new BuildingPlan(plugin, name, name+".schematic", null);
            plugin.getDataStore().saveBuildingPlan(plan);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (DataException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return plan;
    }

    public File getSchematicFile() {
        return new File(new File(plugin.getDataFolder(), "schematics"), filename);
    }

    public CuboidClipboard getRotatedClipboard(BlockFace facing) {
        try {
            CuboidClipboard clipboard = SchematicFormat.MCEDIT.load(this.getSchematicFile());
            clipboard.rotate2D(getRotationDegrees(BlockFace.NORTH, facing));
            clipboard.setOffset(findEnderChest(clipboard));
            return clipboard;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (DataException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean sendPreview(Player player, Block enderChest) {
        Location chestLoc = enderChest.getLocation();
        EnderChest ec = (EnderChest) chestLoc.getBlock().getState().getData();
        BlockFace dir = ec.getFacing();
        CuboidClipboard clipboard = getRotatedClipboard(dir);
        Vector offset = clipboard.getOffset();
        Vector origin = new Vector(enderChest.getX(), enderChest.getY(), enderChest.getZ()).add(offset);
        int chestY = -clipboard.getOffset().getBlockY();
        for (int x = 0; x < clipboard.getSize().getBlockX(); x++) {
            for (int y = 0; y < clipboard.getSize().getBlockY(); y++) {
                for (int z = 0; z < clipboard.getSize().getBlockZ(); z++) {
                    Vector v = new Vector(origin).add(x, y, z);
                    Location loc = new Location(player.getWorld(), v.getBlockX(), v.getBlockY(), v.getBlockZ());
                    BaseBlock bb = clipboard.getPoint(new Vector(x,y,z));
                    if (bb.getType() != 0) {
                        if (coverableBlocks.contains(loc.getBlock().getType()) || y < chestY) {
                            player.sendBlockChange(loc, bb.getType(), (byte)bb.getData());
                        } else if (loc.equals(chestLoc)) {
                            // skip the enderchest.
                        } else {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    public void clearPreview(String playerName, Block enderChest) {
        Player player = plugin.getServer().getPlayer(playerName);
        Location chestLoc = enderChest.getLocation();
        EnderChest ec = (EnderChest) chestLoc.getBlock().getState().getData();
        BlockFace dir = ec.getFacing();
        CuboidClipboard clipboard = getRotatedClipboard(dir);
        Vector offset = clipboard.getOffset();
        Vector origin = new Vector(enderChest.getX(), enderChest.getY(), enderChest.getZ()).add(offset);
        plugin.getLogger().info("Schematic Offset: " + offset);
        for (int x = 0; x < clipboard.getSize().getBlockX(); x++) {
            for (int y = 0; y < clipboard.getSize().getBlockY(); y++) {
                for (int z = 0; z < clipboard.getSize().getBlockZ(); z++) {
                    Vector v = new Vector(origin).add(x, y, z);
                    Location loc = new Location(player.getWorld(), v.getBlockX(), v.getBlockY(), v.getBlockZ());
                    BaseBlock bb = clipboard.getPoint(new Vector(x,y,z));
                    if (bb.getType() > 0) {
                        player.sendBlockChange(loc, loc.getBlock().getTypeId(), loc.getBlock().getData());
                    }
                }
            }
        }
    }

    public void build(Block enderChest, CuboidClipboard clipboard) {
        Location chestLoc = enderChest.getLocation();
        EnderChest ec = (EnderChest) Material.getMaterial(BuildInABox.BLOCK_ID).getNewData(chestLoc.getBlock().getData());
        BlockFace dir = ec.getFacing();
        if (clipboard == null) {
            clipboard = getRotatedClipboard(dir);
        }
        EditSession editSession = new EditSession(new BukkitWorld(chestLoc.getWorld()), 500000);
        editSession.setFastMode(true);
        editSession.enableQueue();
        Vector origin = new Vector(enderChest.getX(), enderChest.getY(), enderChest.getZ());
        try {
            clipboard.paste(editSession, origin, true, true);
            editSession.flushQueue();
            if (plugin.getConfig().getBoolean("protect-buildings", false)) {
                protectBlocks(enderChest, clipboard);
            }
        } catch (MaxChangedBlocksException e) {
            e.printStackTrace();
        }
    }

    public Set<Chunk> protectBlocks(Block enderChest, CuboidClipboard clipboard) {
        HashSet<Chunk> loadedChunks = new HashSet<Chunk>();
        if (clipboard == null) {
            Location chestLoc = enderChest.getLocation();
            EnderChest ec = (EnderChest) Material.getMaterial(BuildInABox.BLOCK_ID).getNewData(chestLoc.getBlock().getData());
            BlockFace dir = ec.getFacing();
            clipboard = getRotatedClipboard(dir);
        }
        Location loc;
        Vector offset = clipboard.getOffset();
        Vector origin = new Vector(enderChest.getX(), enderChest.getY(), enderChest.getZ());
        for (int x=0;x<clipboard.getSize().getBlockX();x++) {
            for (int y = 0;y<clipboard.getSize().getBlockY();y++) {
                for (int z=0;z<clipboard.getSize().getBlockZ();z++) {
                    if (clipboard.getPoint(new Vector(x,y,z)).getType() > 0) {
                        Vector v = origin.add(offset);
                        loc = new Location(enderChest.getWorld(), v.getBlockX()+x, v.getBlockY()+y, v.getBlockZ()+z);
                        loadedChunks.add(loc.getChunk());
                        enderChest.getWorld().getBlockAt(loc).setMetadata("biab-block", new FixedMetadataValue(plugin, enderChest));
                    }
                }
            }
        }
        return loadedChunks;
    }

    public String getFilename() {
        return filename;
    }

    public List<String> getDescription() {
        return description;
    }

}
