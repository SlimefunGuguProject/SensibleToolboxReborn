package io.github.thebusybiscuit.sensibletoolbox.blocks;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPhysicsEvent;
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
import io.github.thebusybiscuit.sensibletoolbox.utils.IntRange;

import me.desht.dhutils.Debugger;

public class BlockUpdateDetector extends BaseSTBBlock {

    private long lastPulse;
    private int duration;
    private int quiet;
    private boolean active = false;

    public BlockUpdateDetector() {
        quiet = 1;
        duration = 2;
    }

    public BlockUpdateDetector(ConfigurationSection conf) {
        super(conf);
        setDuration(conf.getInt("duration"));
        setQuiet(conf.getInt("quiet"));
    }

    @Override
    public YamlConfiguration freeze() {
        YamlConfiguration conf = super.freeze();
        conf.set("duration", getDuration());
        conf.set("quiet", getQuiet());
        return conf;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
        update(false);
    }

    public int getQuiet() {
        return quiet;
    }

    public void setQuiet(int quiet) {
        this.quiet = quiet;
        update(false);
    }

    @Override
    public Material getMaterial() {
        return active ? Material.REDSTONE_BLOCK : Material.PURPLE_TERRACOTTA;
    }

    @Override
    public String getItemName() {
        return "节制型侦测器";
    }

    @Override
    public String[] getLore() {
        return new String[] { "相邻方块更新时发出红石信号", "右键以" + ChatColor.WHITE + "配置此机器" };
    }

    @Override
    public String[] getExtraLore() {
        return new String[] { "持续时间: " + ChatColor.GOLD + getDuration() + " 刻", "休眠时间: " + ChatColor.GOLD + getQuiet() + " 刻", };
    }

    @Override
    public Recipe getMainRecipe() {
        ShapedRecipe res = new ShapedRecipe(getKey(), toItemStack());
        res.shape("SRS", "SPS", "STS");
        res.setIngredient('S', Material.STONE);
        res.setIngredient('P', Material.STICKY_PISTON);
        res.setIngredient('R', Material.REDSTONE);
        res.setIngredient('T', Material.REDSTONE_TORCH);
        return res;
    }

    @Override
    public void onBlockPhysics(BlockPhysicsEvent e) {
        Block b = e.getBlock();
        long timeNow = getLocation().getWorld().getFullTime();
        Debugger.getInstance().debug(this + ": STB侦测器更新: time=" + timeNow + ", lastPulse=" + lastPulse + ", duration=" + getDuration());

        if (timeNow - lastPulse > getDuration() + getQuiet() && isRedstoneActive()) {
            // emit a signal for one or more ticks
            lastPulse = timeNow;
            active = true;
            repaint(b);

            Bukkit.getScheduler().runTaskLater(getProviderPlugin(), () -> {
                active = false;
                repaint(b);
            }, duration);
        }
    }

    @Override
    public void onInteractBlock(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && !e.getPlayer().isSneaking()) {
            getGUI().show(e.getPlayer());
            e.setCancelled(true);
        }

        super.onInteractBlock(e);
    }

    @Override
    protected InventoryGUI createGUI() {
        InventoryGUI gui = GUIUtil.createGUI(this, 9, ChatColor.DARK_PURPLE + getItemName());

        gui.addGadget(new NumericGadget(gui, 1, "持续时间", new IntRange(1, Integer.MAX_VALUE), getDuration(), 10, 1, newValue -> {
            setDuration(newValue);
            return true;
        }));

        gui.addGadget(new NumericGadget(gui, 0, "休眠时间", new IntRange(0, Integer.MAX_VALUE), getQuiet(), 10, 1, newValue -> {
            setQuiet(newValue);
            return true;
        }));

        gui.addGadget(new RedstoneBehaviourGadget(gui, 8));
        gui.addGadget(new AccessControlGadget(gui, 7));
        return gui;
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
