package me.desht.sensibletoolbox.items;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import me.desht.dhutils.Debugger;
import me.desht.dhutils.ItemNames;
import me.desht.dhutils.cost.ItemCost;
import me.desht.sensibletoolbox.api.SensibleToolbox;
import me.desht.sensibletoolbox.api.energy.Chargeable;
import me.desht.sensibletoolbox.api.items.BaseSTBItem;
import me.desht.sensibletoolbox.api.util.BlockProtection;
import me.desht.sensibletoolbox.api.util.PopupMessage;
import me.desht.sensibletoolbox.api.util.STBUtil;
import me.desht.sensibletoolbox.api.util.VanillaInventoryUtils;
import me.desht.sensibletoolbox.items.components.IntegratedCircuit;
import me.desht.sensibletoolbox.items.energycells.TenKEnergyCell;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.material.Directional;
import org.bukkit.material.MaterialData;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

public class MultiBuilder extends BaseSTBItem implements Chargeable {
    private static final MaterialData md = new MaterialData(Material.GOLD_AXE);
    public static final int MAX_BUILD_BLOCKS = 9;
    public static final int DEF_SCU_PER_OPERATION = 40;
    private static final Map<UUID, LinkedBlockingQueue<SwapRecord>> swapQueues = Maps.newHashMap();
    private Mode mode;
    private double charge;
    private MaterialData mat;

    public MultiBuilder() {
        super();
        mode = Mode.BUILD;
        charge = 0;
    }

    public MultiBuilder(ConfigurationSection conf) {
        super(conf);
        mode = Mode.valueOf(conf.getString("mode"));
        charge = conf.getDouble("charge");
        String s = conf.getString("material");
        mat = s.isEmpty() ? null : thawMaterialData(s);
    }

    private MaterialData thawMaterialData(String s) {
        String[] f = s.split(":");
        Material mat = Material.valueOf(f[0]);
        byte data = f.length > 1 ? Byte.parseByte(f[1]) : 0;
        return mat.getNewData(data);
    }

    private String freezeMaterialData(MaterialData mat) {
        return mat.getItemType().toString() + ":" + Byte.toString(mat.getData());
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public double getCharge() {
        return charge;
    }

    public void setCharge(double charge) {
        this.charge = charge;
    }

    public int getMaxCharge() {
        return 10000;
    }

    @Override
    public int getChargeRate() {
        return 100;
    }

    @Override
    public YamlConfiguration freeze() {
        YamlConfiguration map = super.freeze();
        map.set("mode", mode.toString());
        map.set("charge", charge);
        map.set("material", mat == null ? "" : freezeMaterialData(mat));
        return map;
    }

    @Override
    public String getItemName() {
        return "Multibuilder";
    }

    @Override
    public String[] getLore() {
        switch (getMode()) {
            case BUILD:
                return new String[]{
                        "L-click block: " + ChatColor.RESET + "preview",
                        "R-click block: " + ChatColor.RESET + "build",
                        "⇧ + R-click block: " + ChatColor.RESET + "build one",
                        "⇧ + mouse-wheel: " + ChatColor.RESET + "EXCHANGE mode"
                };
            case EXCHANGE:
                return new String[]{
                        "L-click block: " + ChatColor.RESET + "exchange one block",
                        "R-click block: " + ChatColor.RESET + "exchange many blocks",
                        "⇧ + R-click block: " + ChatColor.RESET + "set target block",
                        "⇧ + mouse-wheel: " + ChatColor.RESET + "BUILD mode"
                };
            default:
                return new String[0];
        }
    }

    @Override
    public String[] getExtraLore() {
        return new String[]{STBUtil.getChargeString(this)};
    }

    @Override
    public Recipe getRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(toItemStack());
        TenKEnergyCell cell = new TenKEnergyCell();
        cell.setCharge(0.0);
        IntegratedCircuit sc = new IntegratedCircuit();
        registerCustomIngredients(cell, sc);
        recipe.shape(" DP", "CED", "I  ");
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('P', Material.DIAMOND_AXE);
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('E', STBUtil.makeWildCardMaterialData(cell));
        recipe.setIngredient('C', sc.toItemStack().getData());
        return recipe;
    }

    @Override
    public MaterialData getMaterialData() {
        return md;
    }

    @Override
    public String getDisplaySuffix() {
        switch (getMode()) {
            case BUILD:
                return "Build";
            case EXCHANGE:
                String s = mat == null ? "" : " [" + ItemNames.lookup(mat.toItemStack(1)) + "]";
                return "Swap " + s;
            default:
                return null;
        }
    }

    @Override
    public void onInteractItem(PlayerInteractEvent event) {
        switch (getMode()) {
            case BUILD:
                handleBuildMode(event);
                break;
            case EXCHANGE:
                handleExchangeMode(event);
                break;
        }
    }

    @Override
    public void onItemHeld(PlayerItemHeldEvent event) {
        int delta = event.getNewSlot() - event.getPreviousSlot();
        if (delta == 0) {
            return;
        } else if (delta >= 6) {
            delta -= 9;
        } else if (delta <= -6) {
            delta += 9;
        }
        delta = (delta > 0) ? 1 : -1;
        int o = getMode().ordinal() + delta;
        if (o < 0) {
            o = Mode.values().length - 1;
        } else if (o >= Mode.values().length) {
            o = 0;
        }
        setMode(Mode.values()[o]);
        event.getPlayer().setItemInHand(toItemStack());
    }

    private void handleExchangeMode(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (player.isSneaking()) {
                // set the target material
                mat = clicked.getType().getNewData(clicked.getData());
                player.setItemInHand(toItemStack());
            } else if (mat != null) {
                // replace multiple blocks
                int sharpness = player.getItemInHand().getEnchantmentLevel(Enchantment.DAMAGE_ALL);
                int layers = 3 + sharpness;
                startSwap(event.getPlayer(), this, clicked, mat, layers);
                Debugger.getInstance().debug(this + ": replacing " + layers + " layers of blocks");
            }
            event.setCancelled(true);
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK && mat != null) {
            // replace a single block
            startSwap(event.getPlayer(), this, clicked, mat, 0);
            event.setCancelled(true);
        }
    }

    private void startSwap(Player player, MultiBuilder builder, Block origin, MaterialData target, int maxBlocks) {
        LinkedBlockingQueue<SwapRecord> queue = swapQueues.get(player.getWorld().getUID());
        if (queue == null) {
            queue = new LinkedBlockingQueue<SwapRecord>();
            swapQueues.put(player.getWorld().getUID(), queue);
        }
        if (queue.isEmpty()) {
            new QueueSwapper(queue).runTaskTimer(SensibleToolbox.getPluginInstance(), 1L, 1L);
        }

        int chargePerOp = getItemConfig().getInt("scu_per_op", DEF_SCU_PER_OPERATION);
        double chargeNeeded = chargePerOp * Math.pow(0.8, player.getItemInHand().getEnchantmentLevel(Enchantment.DIG_SPEED));
        queue.offer(new SwapRecord(player, origin, origin.getType().getNewData(origin.getData()), target, maxBlocks, builder, -1, chargeNeeded));
    }

    private int howMuchDoesPlayerHave(Player p, MaterialData mat) {
        int amount = 0;
        for (ItemStack stack : p.getInventory()) {
            if (stack != null && !stack.hasItemMeta() && stack.getType() == mat.getItemType() &&
                    (losesDataWhenBroken(mat) || stack.getData().getData() == mat.getData())) {
                amount += stack.getAmount();
            }
        }
        return amount;
    }

    private boolean losesDataWhenBroken(MaterialData mat) {
        // If a material loses its data when in item form (i.e. the block data
        // is used to store the block's orientation), then we need to know that
        // to correctly match what the player has in inventory.
        return mat instanceof Directional;
    }

    private boolean canReplace(Player player, Block b) {
        // we won't replace any block which can hold items, or any STB block, or any unbreakable block
        if (SensibleToolbox.getBlockAt(b.getLocation(), true) != null) {
            return false;
        } else if (VanillaInventoryUtils.isVanillaInventory(b)) {
            return false;
        } else if (STBUtil.getMaterialHardness(b.getType()) == Double.MAX_VALUE) {
            return false;
        } else {
            return SensibleToolbox.getBlockProtection().playerCanBuild(player, b, BlockProtection.Operation.BREAK);
        }
    }

    private void handleBuildMode(PlayerInteractEvent event) {
        final Player player = event.getPlayer();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            final List<Block> blocks = getBuildCandidates(player, event.getClickedBlock(), event.getBlockFace());
            MaterialData matData = event.getClickedBlock().getType().getNewData(event.getClickedBlock().getData());
            int nAffected = Math.min(blocks.size(), howMuchDoesPlayerHave(player, matData));
            List<Block> actualBlocks = blocks.subList(0, nAffected);

            if (!actualBlocks.isEmpty()) {
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    doBuild(player, event.getClickedBlock(), actualBlocks, matData);
                } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    showBuildPreview(player, actualBlocks);
                }
            }
            event.setCancelled(true);
        }
    }

    private void showBuildPreview(final Player player, final List<Block> blocks) {
        Bukkit.getScheduler().runTask(getProviderPlugin(), new Runnable() {
            @Override
            public void run() {
                for (Block b : blocks) {
                    player.sendBlockChange(b.getLocation(), Material.STAINED_GLASS, DyeColor.WHITE.getWoolData());
                }
            }
        });
        Bukkit.getScheduler().runTaskLater(getProviderPlugin(), new Runnable() {
            @Override
            public void run() {
                for (Block b : blocks) {
                    player.sendBlockChange(b.getLocation(), b.getType(), b.getData());
                }
            }
        }, 20L);
    }

    private void doBuild(Player player, Block source, List<Block> actualBlocks, MaterialData matData) {
        ItemStack inHand = player.getItemInHand();
        int chargePerOp = getItemConfig().getInt("scu_per_op", DEF_SCU_PER_OPERATION);
        double chargeNeeded = chargePerOp * actualBlocks.size() * Math.pow(0.8, inHand.getEnchantmentLevel(Enchantment.DIG_SPEED));
        if (getCharge() >= chargeNeeded) {
            setCharge(getCharge() - chargeNeeded);
            ItemCost cost = losesDataWhenBroken(matData) ?
                    new ItemCost(matData.getItemType(), actualBlocks.size()) :
                    new ItemCost(new ItemStack(source.getType(), actualBlocks.size(), source.getData()));
            cost.apply(player);
            for (Block b : actualBlocks) {
                b.setTypeIdAndData(source.getType().getId(), source.getData(), true);
            }
            player.setItemInHand(toItemStack());
            player.playSound(player.getLocation(), Sound.DIG_STONE, 1.0f, 1.0f);
        } else {
            PopupMessage.quickMessage(player, source.getLocation(), ChatColor.RED + "Not enough power!");
            player.playSound(player.getLocation(), Sound.CLICK, 1.0f, 0.5f);
        }
    }

    private List<Block> getBuildCandidates(Player player, Block clickedBlock, BlockFace blockFace) {
        int sharpness = player.getItemInHand().getEnchantmentLevel(Enchantment.DAMAGE_ALL);
        int max = MAX_BUILD_BLOCKS + sharpness * 2;
        if (player.isSneaking()) {
            max = 1;
        }
        Set<Block> blocks = new HashSet<Block>(max * 4 / 3, 0.75f);
        floodFill2D(player, clickedBlock.getRelative(blockFace),
                new MaterialData(clickedBlock.getType(), clickedBlock.getData()),
                blockFace.getOppositeFace(), getBuildFaces(blockFace), max, blocks);
        return Lists.newArrayList(blocks);
    }

    private void floodFill2D(Player player, Block b, MaterialData target, BlockFace face, BlockFace[] faces, int max, Set<Block> blocks) {
        Block b0 = b.getRelative(face);
        if (!b.isEmpty() && !b.isLiquid() || b0.getType() != target.getItemType() || b0.getData() != target.getData()
                || blocks.size() > max || blocks.contains(b) || !canReplace(player, b)) {
            return;
        }
        blocks.add(b);
        for (BlockFace dir : faces) {
            floodFill2D(player, b.getRelative(dir), target, face, faces, max, blocks);
        }
    }

    private BlockFace[] getBuildFaces(BlockFace face) {
        switch (face) {
            case NORTH:
            case SOUTH:
                return BuildFaces.ns;
            case EAST:
            case WEST:
                return BuildFaces.ew;
            case UP:
            case DOWN:
                return BuildFaces.ud;
        }
        throw new IllegalArgumentException("invalid face: " + face);
    }

    private enum Mode {
        BUILD, EXCHANGE
    }

    private static class BuildFaces {
        private static final BlockFace[] ns = {BlockFace.EAST, BlockFace.DOWN, BlockFace.WEST, BlockFace.UP};
        private static final BlockFace[] ew = {BlockFace.NORTH, BlockFace.DOWN, BlockFace.SOUTH, BlockFace.UP};
        private static final BlockFace[] ud = {BlockFace.EAST, BlockFace.NORTH, BlockFace.WEST, BlockFace.SOUTH};
    }

    private class QueueSwapper extends BukkitRunnable {
        private final LinkedBlockingQueue<SwapRecord> queue;
        private final ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);  // ensure we can mine anything

        public QueueSwapper(LinkedBlockingQueue<SwapRecord> queue) {
            this.queue = queue;
        }

        @Override
        public void run() {
            boolean didWork = false;

            while (!didWork) {
                // first, some validation & sanity checking...
                SwapRecord rec = queue.poll();
                if (rec == null) {
                    cancel();
                    return;
                }

                if (!rec.player.isOnline()) {
                    continue;
                }
                Block b = rec.block;
                if (b.getType() == rec.target.getItemType() && b.getData() == rec.target.getData()
                        || rec.builder.getCharge() < rec.chargeNeeded
                        || !canReplace(rec.player, rec.block)) {
                    continue;
                }

                // (hopefully) take materials from the player...
                int slot = rec.slot;
                PlayerInventory inventory = rec.player.getInventory();
                if (slot < 0 || inventory.getItem(slot) == null) {
                    slot = getSlotForItem(rec.player, rec.target);
                    if (slot == -1) {
                        // player is out of materials to swap: scan the queue and remove any other
                        // records for this player & material, to avoid constant inventory rescanning
                        Iterator<SwapRecord> iter = queue.iterator();
                        while (iter.hasNext()) {
                            SwapRecord r = iter.next();
                            if (r.player.equals(rec.player) && r.target.equals(rec.target)) {
                                iter.remove();
                            }
                        }
                        continue;
                    }
                }
                ItemStack item = inventory.getItem(slot);
                item.setAmount(item.getAmount() - 1);
                inventory.setItem(slot, item.getAmount() > 0 ? item : null);

                // take SCU from the multibuilder...
                rec.builder.setCharge(rec.builder.getCharge() - rec.chargeNeeded);
                ItemStack builderItem = rec.builder.toItemStack();
                rec.player.setItemInHand(builderItem);

                // give materials to the player...
                if (builderItem.getEnchantmentLevel(Enchantment.SILK_TOUCH) == 1) {
                    tool.addEnchantment(Enchantment.SILK_TOUCH, 1);
                } else {
                    tool.removeEnchantment(Enchantment.SILK_TOUCH);
                }
                for (ItemStack stack : STBUtil.calculateDrops(b, tool)) {
                    STBUtil.giveItems(rec.player, stack);
                }

                // make the actual in-world swap
                b.getWorld().playEffect(b.getLocation(), Effect.STEP_SOUND, b.getType());
                b.setTypeIdAndData(rec.target.getItemTypeId(), rec.target.getData(), true);

                // queue up the next set of blocks
                if (rec.layersLeft > 0) {
                    for (int x = -1; x <= 1; x++) {
                        for (int y = -1; y <= 1; y++) {
                            for (int z = -1; z <= 1; z++) {
                                Block b1 = b.getRelative(x, y, z);
                                if ((x != 0 || y != 0 || z != 0) && b1.getType() == rec.source.getItemType() && b1.getData() == rec.source.getData() && STBUtil.isExposed(b1)) {
                                    queue.offer(new SwapRecord(rec.player, b1, rec.source, rec.target, rec.layersLeft - 1, rec.builder, slot, rec.chargeNeeded));
                                }
                            }
                        }
                    }
                }

                didWork = true;
            }
        }

        private int getSlotForItem(Player player, MaterialData from) {
            for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack != null && stack.getType() == from.getItemType() && (losesDataWhenBroken(from) || stack.getDurability() == from.getData()) && !stack.hasItemMeta()) {
                    return slot;
                }
            }
            return -1;
        }
    }

    private class SwapRecord {
        private final Player player;
        private final Block block;
        private final MaterialData source;
        private final MaterialData target;
        private final int layersLeft;
        private final MultiBuilder builder;
        private final int slot;
        private final double chargeNeeded;

        private SwapRecord(Player player, Block block, MaterialData source, MaterialData target, int layersLeft, MultiBuilder builder, int slot, double chargeNeeded) {
            this.player = player;
            this.block = block;
            this.source = source;
            this.target = target;
            this.layersLeft = layersLeft;
            this.builder = builder;
            this.slot = slot;
            this.chargeNeeded = chargeNeeded;
        }
    }
}
