package io.github.thebusybiscuit.sensibletoolbox.blocks;

import java.awt.Color;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

import io.github.thebusybiscuit.sensibletoolbox.api.RedstoneBehaviour;
import io.github.thebusybiscuit.sensibletoolbox.api.gui.GUIUtil;
import io.github.thebusybiscuit.sensibletoolbox.api.gui.InventoryGUI;
import io.github.thebusybiscuit.sensibletoolbox.api.gui.gadgets.AccessControlGadget;
import io.github.thebusybiscuit.sensibletoolbox.api.gui.gadgets.NumericGadget;
import io.github.thebusybiscuit.sensibletoolbox.api.gui.gadgets.RedstoneBehaviourGadget;
import io.github.thebusybiscuit.sensibletoolbox.api.items.BaseSTBBlock;
import io.github.thebusybiscuit.sensibletoolbox.api.items.BaseSTBItem;
import io.github.thebusybiscuit.sensibletoolbox.utils.IntRange;

public class RedstoneClock extends BaseSTBBlock {

    private int interval;
    private int onDuration;
    private boolean active = false;

    public RedstoneClock() {
        interval = 20;
        onDuration = 5;
    }

    public RedstoneClock(ConfigurationSection conf) {
        super(conf);
        setInterval(conf.contains("interval") ? conf.getInt("interval") : conf.getInt("frequency"));
        setOnDuration(conf.getInt("onDuration"));
        active = conf.getBoolean("active", false);
    }

    @Override
    protected InventoryGUI createGUI() {
        InventoryGUI gui = GUIUtil.createGUI(this, 9, ChatColor.DARK_RED + getItemName());

        gui.addGadget(new NumericGadget(gui, 0, "间隔", new IntRange(1, Integer.MAX_VALUE), getInterval(), 10, 1, newValue -> {
            if (newValue > getOnDuration()) {
                setInterval(newValue);
                return true;
            } else {
                return false;
            }
        }));

        gui.addGadget(new NumericGadget(gui, 1, "持续时间", new IntRange(1, Integer.MAX_VALUE), getOnDuration(), 10, 1, newValue -> {
            if (newValue < getInterval()) {
                setOnDuration(newValue);
                return true;
            } else {
                return false;
            }
        }));

        gui.addGadget(new RedstoneBehaviourGadget(gui, 8));
        gui.addGadget(new AccessControlGadget(gui, 7));
        return gui;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
        update(false);
    }

    public int getOnDuration() {
        return onDuration;
    }

    public void setOnDuration(int onDuration) {
        this.onDuration = onDuration;
        update(false);
    }

    @Override
    public YamlConfiguration freeze() {
        YamlConfiguration conf = super.freeze();
        conf.set("interval", interval);
        conf.set("onDuration", onDuration);
        conf.set("active", active);
        return conf;
    }

    @Override
    public Material getMaterial() {
        return active ? Material.REDSTONE_BLOCK : Material.RED_TERRACOTTA;
    }

    @Override
    public String getItemName() {
        return "红石钟";
    }

    @Override
    public String[] getLore() {
        return new String[] { "有间隔地发出红石信号" + "右键以" + ChatColor.WHITE + "配置此机器" };
    }

    @Override
    public Recipe getMainRecipe() {
        ShapedRecipe res = new ShapedRecipe(getKey(), toItemStack());
        res.shape("RSR", "STS", "RSR");
        res.setIngredient('R', Material.REDSTONE);
        res.setIngredient('S', Material.STONE);
        res.setIngredient('T', Material.REDSTONE_TORCH);
        return res;
    }

    @Override
    public String[] getExtraLore() {
        String l = BaseSTBItem.LORE_COLOR + "间隔: " + ChatColor.GOLD + getInterval() + LORE_COLOR + "t, 持续时间: " + ChatColor.GOLD + getOnDuration() + LORE_COLOR + "t";
        return new String[] { l };
    }

    @Override
    public int getTickRate() {
        return 1;
    }

    @Override
    public void onServerTick() {
        Location l = getLocation();
        Block b = l.getBlock();
        long time = getTicksLived();

        if (time % getInterval() == 0 && isRedstoneActive()) {
            // power up
            active = true;
            repaint(b);
        } else if (time % getInterval() == getOnDuration()) {
            // power down
            active = false;
            repaint(b);
        }

        if (time % 50 == 10) {
            playParticles(new Color(255, 0, 0));
        }

        super.onServerTick();
    }

    public void playParticles(Color color) {
        // try {
        // Location l = getLocation().add(0.6, 1, 0.3);
        // ParticleEffect.REDSTONE.displayColoredParticle(l, color);
        // l = getLocation().add(1.6, 1, 0.1);
        // ParticleEffect.REDSTONE.displayColoredParticle(l, color);
        // l = getLocation().add(0.6, 0.5, -0.2);
        // ParticleEffect.REDSTONE.displayColoredParticle(l, color);
        // l = getLocation().add(0.4, 0.8, 0.6);
        // ParticleEffect.REDSTONE.displayColoredParticle(l, color);
        // l = getLocation().add(0.3, 0.6, 1.6);
        // ParticleEffect.REDSTONE.displayColoredParticle(l, color);
        // l = getLocation().add(-0.2, 0.3, 0.6);
        // ParticleEffect.REDSTONE.displayColoredParticle(l, color);
        // l = getLocation().add(1.6, 0.7, 0.3);
        // ParticleEffect.REDSTONE.displayColoredParticle(l, color);
        // } catch (Exception e) {
        // e.printStackTrace();
        // }
    }

    @Override
    public void onInteractBlock(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && !e.getPlayer().isSneaking()) {
            getGUI().show(e.getPlayer());
            e.setCancelled(true);
        }
    }

    @Override
    public void onBlockUnregistered(Location l) {
        // ensure the non-active form of the item is always dropped
        active = false;
        super.onBlockUnregistered(l);
    }

    @Override
    public boolean supportsRedstoneBehaviour(RedstoneBehaviour behaviour) {
        return behaviour != RedstoneBehaviour.PULSED;
    }
}
