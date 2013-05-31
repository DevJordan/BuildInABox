package com.norcode.bukkit.buildinabox;

import java.io.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;


import com.norcode.bukkit.schematica.Clipboard;
import com.norcode.bukkit.schematica.ClipboardBlock;
import com.norcode.bukkit.schematica.Session;
import com.norcode.bukkit.schematica.exceptions.IncompleteSelectionException;
import com.norcode.bukkit.schematica.exceptions.SchematicLoadException;
import com.norcode.bukkit.schematica.exceptions.SchematicSaveException;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import org.bukkit.material.Directional;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import org.bukkit.Material;

import org.bukkit.util.BlockVector;

public class BuildingPlan {
    String name;
    String displayName;
    String filename;
    List<String> description;
    BuildInABox plugin;
    
    public static final EnumSet<Material> coverableBlocks = EnumSet.of(Material.LONG_GRASS, Material.SNOW, Material.AIR, Material.RED_MUSHROOM, Material.BROWN_MUSHROOM, Material.DEAD_BUSH, Material.FIRE, Material.RED_ROSE, Material.YELLOW_FLOWER, Material.SAPLING);


    public BuildingPlan(BuildInABox plugin, String name, String filename, String displayName, List<String> description) {
        this.plugin = plugin;
        this.name = name;
        this.displayName = displayName;
        this.filename = filename;
        this.description = description;
        registerPermissions();
    }

    private void registerPermissions() {
        registerPermission("give", plugin.wildcardGivePerm);
        registerPermission("place", plugin.wildcardPlacePerm);
        registerPermission("pickup", plugin.wildcardPickupPerm);
        registerPermission("lock", plugin.wildcardLockPerm);
        registerPermission("unlock", plugin.wildcardUnlockPerm);
    }

    public void unregisterPermissions() {
        PluginManager pm = plugin.getServer().getPluginManager();
        pm.removePermission("biab.give." + name.toLowerCase());
        pm.removePermission("biab.place." + name.toLowerCase());
        pm.removePermission("biab.pickup." + name.toLowerCase());
        pm.removePermission("biab.lock." + name.toLowerCase());
        pm.removePermission("biab.unlock." + name.toLowerCase());
    }

    private void registerPermission(String action, Permission parent) {
        Permission p = plugin.getServer().getPluginManager().getPermission("biab." + action.toLowerCase() + "." + name.toLowerCase());
        if (p == null) {
            p = new Permission("biab." + action.toLowerCase() + "." + name.toLowerCase(), "Permission to " + action + " " + name + " BIABs.", PermissionDefault.OP);
            p.addParent(parent, true);
            plugin.getServer().getPluginManager().addPermission(p);
            parent.recalculatePermissibles();
        }
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void setDescription(List<String> description) {
        this.description = description;
    }

    public String getName() {
        return this.name;
    }

    public String getDisplayName() {
        return (displayName == null || displayName.equals("")) ? getName() : displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }


    public static BlockVector findEnderChest(Clipboard cc) {
        BlockVector size = cc.getSize();
        BuildInABox.getInstance().debug("searching a " + size.getBlockX() + "x" + size.getBlockY() + "x" + size.getBlockZ() + " area for EnderChests");
        for (int x = 0; x < size.getBlockX(); x++) {
            for (int y = 0; y < size.getBlockY(); y++) {
                for (int z = 0; z < size.getBlockZ(); z++) {
                    ClipboardBlock block = cc.getBlock(x, y, z);
                    BuildInABox.getInstance().debug("searching " + x + "," + y + "," + z + "  for enderchest");
                    if (block.getType() == BuildInABox.getInstance().cfg.getChestBlockId()) {
                        return new BlockVector(-x, -y, -z);
                    }
                }
            }
        }
        return null;
    }

    public static BuildingPlan fromClipboard(BuildInABox plugin, Player player, String name) throws SchematicSaveException {
        BuildingPlan plan = null;
        Session session = plugin.getPlayerSession(player);
        try {
            session.copy();
            BuildInABox.getInstance().debug("Clipboard Copied: " + session.getClipboard());
        } catch (IncompleteSelectionException e) {
            return null;
        }

        Clipboard clipboard = session.getClipboard();

        BlockVector chestOffset = findEnderChest(clipboard);
        if (chestOffset == null) {
            player.sendMessage(BuildInABox.getErrorMsg("enderchest-not-found"));
            return null;
        }
        clipboard.setOffset(chestOffset);
        Directional md = (Directional) Material.getMaterial(BuildInABox.getInstance().cfg.getChestBlockId()).getNewData(clipboard.getBlock(-chestOffset.getBlockX(), -chestOffset.getBlockY(), -chestOffset.getBlockZ()).getData());
        if (!md.getFacing().equals(BlockFace.NORTH)) {
            int deg = BuildInABox.getRotationDegrees(md.getFacing(), BlockFace.NORTH);
            BuildInABox.getInstance().debug("rotating " + deg + " degrees");
            clipboard.rotate2D(deg);

            BuildInABox.getInstance().debug("Rotated Clipboard Dimensions: " + clipboard.toString());
            chestOffset = findEnderChest(clipboard);
        }
        clipboard.setOffset(chestOffset);
        File outFile = new File(new File(plugin.getDataFolder(), "schematics"), name + ".schematic");
        if (!outFile.exists()) {
            try {
                outFile.createNewFile();
            } catch (IOException ex) {
                throw new SchematicSaveException(ex.getMessage());
            }
        }
        DataOutputStream os = null;
        try {
            os = new DataOutputStream(new FileOutputStream(outFile));
            os.write(clipboard.toSchematic());
        } catch (IOException e) {
            throw new SchematicSaveException(e.getMessage());
        } finally {
            if (os != null) {
                try { os.close(); } catch (IOException e) {};
            }
        }
        plan = new BuildingPlan(plugin, name, name+".schematic", name, null);
        plugin.getDataStore().saveBuildingPlan(plan);
        return plan;
    }

    public File getSchematicFile() {
        return new File(new File(plugin.getDataFolder(), "schematics"), filename);
    }

    public Clipboard getRotatedClipboard(BlockFace facing) {
        DataInputStream is = null;
        byte[] data = null;
        try {
            is = new DataInputStream(new FileInputStream(this.getSchematicFile()));
            data = new byte[is.available()];
            is.readFully(data);
            is.close();
        } catch (IOException e) {
            return null;
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException e) {}
            }
        }
        Clipboard clipboard = null;
        try {
            clipboard = Clipboard.fromSchematic(data);
            clipboard.rotate2D(BuildInABox.getRotationDegrees(BlockFace.NORTH, facing));
            clipboard.setOffset(findEnderChest(clipboard));
        } catch (SchematicLoadException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load schematic.", ex);
        }
        return clipboard;
    }



    public String getFilename() {
        return filename;
    }

    public List<String> getDescription() {
        if (description == null) {
            this.description = new ArrayList<String>();
        }
        return description;
    }

}
