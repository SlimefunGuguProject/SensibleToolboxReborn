package io.github.thebusybiscuit.sensibletoolbox.items.itemroutermodules;

import java.util.Arrays;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Directional;

import io.github.bakedlibs.dough.items.ItemUtils;
import io.github.thebusybiscuit.sensibletoolbox.api.STBInventoryHolder;
import io.github.thebusybiscuit.sensibletoolbox.api.SensibleToolbox;
import io.github.thebusybiscuit.sensibletoolbox.api.filters.Filter;
import io.github.thebusybiscuit.sensibletoolbox.api.filters.FilterType;
import io.github.thebusybiscuit.sensibletoolbox.api.filters.Filtering;
import io.github.thebusybiscuit.sensibletoolbox.api.gui.GUIUtil;
import io.github.thebusybiscuit.sensibletoolbox.api.gui.InventoryGUI;
import io.github.thebusybiscuit.sensibletoolbox.api.gui.SlotType;
import io.github.thebusybiscuit.sensibletoolbox.api.gui.gadgets.DirectionGadget;
import io.github.thebusybiscuit.sensibletoolbox.api.gui.gadgets.FilterTypeGadget;
import io.github.thebusybiscuit.sensibletoolbox.api.gui.gadgets.ToggleButton;
import io.github.thebusybiscuit.sensibletoolbox.api.items.BaseSTBBlock;
import io.github.thebusybiscuit.sensibletoolbox.blocks.router.ItemRouter;
import io.github.thebusybiscuit.sensibletoolbox.utils.UnicodeSymbol;
import io.github.thebusybiscuit.sensibletoolbox.utils.VanillaInventoryUtils;

public abstract class DirectionalItemRouterModule extends ItemRouterModule implements Filtering, Directional {

    private static final String LIST_ITEM = ChatColor.LIGHT_PURPLE + UnicodeSymbol.CENTERED_POINT.toUnicode() + " " + ChatColor.AQUA;

    private static final ItemStack WHITE_BUTTON = GUIUtil.makeTexture(Material.WHITE_WOOL, ChatColor.WHITE.toString() + ChatColor.UNDERLINE + "白名单", "仅处理匹配的物品");
    private static final ItemStack BLACK_BUTTON = GUIUtil.makeTexture(Material.BLACK_WOOL, ChatColor.WHITE.toString() + ChatColor.UNDERLINE + "黑名单", "仅处理不匹配的物品");
    private static final ItemStack OFF_BUTTON = GUIUtil.makeTexture(Material.LIGHT_BLUE_STAINED_GLASS, ChatColor.WHITE.toString() + ChatColor.UNDERLINE + "处理顺序(关)", "当这个升级正在处理物品时，", "机器将继续处理其他物品");
    private static final ItemStack ON_BUTTON = GUIUtil.makeTexture(Material.ORANGE_WOOL, ChatColor.WHITE.toString() + ChatColor.UNDERLINE + "处理顺序(开)", "当这个升级正在处理物品时，", "机器将不会处理其他物品");

    public static final int FILTER_LABEL_SLOT = 0;
    public static final int DIRECTION_LABEL_SLOT = 5;
    private final Filter filter;
    private BlockFace direction;
    private boolean terminator;
    private InventoryGUI gui;
    private final int[] filterSlots = { 1, 2, 3, 10, 11, 12, 19, 20, 21 };

    /**
     * Run this module's action.
     *
     * @param l
     *            the location of the module's owning item router
     * @return true if the module did some work on this tick
     */
    public abstract boolean execute(Location l);

    public DirectionalItemRouterModule() {
        // default filter: blacklist, no items
        filter = new Filter();
        setFacingDirection(BlockFace.SELF);
    }

    public DirectionalItemRouterModule(ConfigurationSection conf) {
        super(conf);
        setFacingDirection(BlockFace.valueOf(conf.getString("direction")));
        setTerminator(conf.getBoolean("terminator", false));

        if (conf.contains("filtered")) {
            boolean isWhite = conf.getBoolean("filterWhitelist", true);
            FilterType filterType = FilterType.valueOf(conf.getString("filterType", "MATERIAL"));
            @SuppressWarnings("unchecked")
            List<ItemStack> l = (List<ItemStack>) conf.getList("filtered");
            filter = Filter.fromItemList(isWhite, l, filterType);
        } else {
            filter = new Filter();
        }
    }

    @Override
    public YamlConfiguration freeze() {
        YamlConfiguration conf = super.freeze();
        conf.set("direction", getFacing().toString());
        conf.set("terminator", isTerminator());

        if (filter != null) {
            conf.set("filtered", filter.getFilterList());
            conf.set("filterWhitelist", filter.isWhiteList());
            conf.set("filterType", filter.getFilterType().toString());
        }
        return conf;
    }

    @Override
    public String[] getExtraLore() {
        if (filter == null) {
            return new String[0];
        } else {
            String[] lore = new String[(filter.size() + 1) / 2 + 2];
            String what = filter.isWhiteList() ? "白名单" : "黑名单";
            lore[0] = ChatColor.GOLD.toString() + filter.size() + " 个物品"  + " " + what;

            if (isTerminator()) {
                lore[0] += ", " + ChatColor.BOLD + "处理顺序(开)";
            }

            lore[1] = ChatColor.GOLD + filter.getFilterType().getLabel();
            int i = 2;

            for (ItemStack stack : filter.getFilterList()) {
                int n = i / 2 + 1;
                String name = ItemUtils.getItemName(stack);
                lore[n] = lore[n] == null ? LIST_ITEM + name : lore[n] + " " + LIST_ITEM + name;
                i++;
            }

            return lore;
        }
    }

    @Override
    public String getDisplaySuffix() {
        return direction != BlockFace.SELF ? direction.toString() : null;
    }

    @Override
    public void setFacingDirection(BlockFace blockFace) {
        direction = blockFace;
    }

    @Override
    public BlockFace getFacing() {
        return direction;
    }

    public Filter getFilter() {
        return filter;
    }

    public boolean isTerminator() {
        return terminator;
    }

    public void setTerminator(boolean terminator) {
        this.terminator = terminator;
    }

    @Override
    public void onInteractItem(PlayerInteractEvent e) {
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            // set module direction based on clicked block face
            setFacingDirection(e.getBlockFace().getOppositeFace());
            e.getPlayer().getInventory().setItem(e.getHand(), toItemStack(e.getItem().getAmount()));
            e.setCancelled(true);
        } else if (e.getAction() == Action.LEFT_CLICK_AIR && e.getPlayer().isSneaking()) {
            // unset module direction
            setFacingDirection(BlockFace.SELF);
            e.getPlayer().getInventory().setItem(e.getHand(), toItemStack(e.getItem().getAmount()));
            e.setCancelled(true);
        } else if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemRouter rtr = e.getClickedBlock() == null ? null : SensibleToolbox.getBlockAt(e.getClickedBlock().getLocation(), ItemRouter.class, true);
            if (e.getClickedBlock() == null || (rtr == null && !e.getClickedBlock().getType().isInteractable())) {
                // open module configuration GUI
                gui = createGUI(e.getPlayer());
                gui.show(e.getPlayer());
                e.setCancelled(true);
            }
        }
    }

    private InventoryGUI createGUI(Player p) {
        InventoryGUI inventory = GUIUtil.createGUI(p, this, 36, ChatColor.DARK_RED + "升级配置");

        inventory.addGadget(new ToggleButton(inventory, 28, getFilter().isWhiteList(), WHITE_BUTTON, BLACK_BUTTON, newValue -> {
            if (getFilter() != null) {
                getFilter().setWhiteList(newValue);
                return true;
            } else {
                return false;
            }
        }));

        inventory.addGadget(new FilterTypeGadget(inventory, 29));
        inventory.addGadget(new ToggleButton(inventory, 30, isTerminator(), ON_BUTTON, OFF_BUTTON, newValue -> {
            setTerminator(newValue);
            return true;
        }));

        inventory.addLabel("过滤物品", FILTER_LABEL_SLOT, null, "最多 9 种物品", "在这个升级中 " + UnicodeSymbol.ARROW_RIGHT.toUnicode());
        for (int slot : filterSlots) {
            inventory.setSlotType(slot, SlotType.ITEM);
        }
        populateFilterInventory(inventory.getInventory());

        inventory.addLabel("方向", DIRECTION_LABEL_SLOT, null, "设置模块在机器中安装后的工作方向");
        ItemStack texture = new ItemStack(new ItemRouter().getMaterial());
        GUIUtil.setDisplayName(texture, "未设置方向");
        inventory.addGadget(new DirectionGadget(inventory, 16, texture));

        return inventory;
    }

    private void populateFilterInventory(Inventory inv) {
        int n = 0;
        for (ItemStack s : filter.getFilterList()) {
            inv.setItem(filterSlots[n], s);
            n++;
            if (n >= filterSlots.length) {
                break;
            }
        }
    }

    protected String[] makeDirectionalLore(String... lore) {
        String[] newLore = Arrays.copyOf(lore, lore.length + 2);
        newLore[lore.length] = "左键方块: " + ChatColor.WHITE + " 设置方向";
        newLore[lore.length + 1] = UnicodeSymbol.ARROW_UP.toUnicode() + " 左键空气: " + ChatColor.WHITE + " 取消设置";
        return newLore;
    }

    @Override
    public boolean onSlotClick(HumanEntity p, int slot, ClickType click, ItemStack inSlot, ItemStack onCursor) {
        if (onCursor.getType() == Material.AIR) {
            gui.getInventory().setItem(slot, null);
        } else {
            ItemStack s = onCursor.clone();
            s.setAmount(1);
            gui.getInventory().setItem(slot, s);
        }
        return false;
    }

    @Override
    public boolean onPlayerInventoryClick(HumanEntity p, int slot, ClickType click, ItemStack inSlot, ItemStack onCursor) {
        return true;
    }

    @Override
    public int onShiftClickInsert(HumanEntity p, int slot, ItemStack toInsert) {
        return 0;
    }

    @Override
    public boolean onShiftClickExtract(HumanEntity p, int slot, ItemStack toExtract) {
        return false;
    }

    @Override
    public boolean onClickOutside(HumanEntity p) {
        return false;
    }

    @Override
    public void onGUIClosed(HumanEntity p) {
        filter.clear();

        for (int slot : filterSlots) {
            ItemStack s = gui.getInventory().getItem(slot);

            if (s != null) {
                filter.addItem(s);
            }
        }

        p.setItemInHand(toItemStack(p.getItemInHand().getAmount()));
    }

    protected boolean doPull(BlockFace from, Location l) {

        if (getItemRouter() != null && getItemRouter().getBufferItem() != null) {
            if (getFilter() != null && !getFilter().shouldPass(getItemRouter().getBufferItem())) {
                return false;
            }
        }
        ItemStack inBuffer = getItemRouter().getBufferItem();

        if (inBuffer != null && inBuffer.getAmount() >= inBuffer.getType().getMaxStackSize()) {
            return false;
        }

        int nToPull = getItemRouter().getStackSize();
        Location targetLoc = getTargetLocation(l);
        ItemStack pulled;
        BaseSTBBlock stb = SensibleToolbox.getBlockAt(targetLoc, true);

        if (stb instanceof STBInventoryHolder) {
            pulled = ((STBInventoryHolder) stb).extractItems(from.getOppositeFace(), inBuffer, nToPull, getItemRouter().getOwner());
        } else {
            // possible vanilla inventory holder
            pulled = VanillaInventoryUtils.pullFromInventory(targetLoc.getBlock(), nToPull, inBuffer, getFilter(), getItemRouter().getOwner());
        }

        if (pulled != null) {
            if (stb != null) {
                stb.update(false);
            }

            getItemRouter().setBufferItem(inBuffer == null ? pulled : inBuffer);
            return true;
        }

        return false;
    }

    protected boolean vanillaInsertion(Block target, int amount, BlockFace face) {
        ItemStack buffer = getItemRouter().getBufferItem();
        int nInserted = VanillaInventoryUtils.vanillaInsertion(target, buffer, amount, face, false, getItemRouter().getOwner());

        if (nInserted == 0) {
            // no insertion happened
            return false;
        } else {
            // some or all items were inserted, buffer size has been adjusted accordingly
            getItemRouter().setBufferItem(buffer.getAmount() == 0 ? null : buffer);
            return true;
        }
    }

    protected Location getTargetLocation(Location l) {
        BlockFace face = getFacing();
        return l.clone().add(face.getModX(), face.getModY(), face.getModZ());
    }
}
