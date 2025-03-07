package io.github.thebusybiscuit.sensibletoolbox.api.gui.gadgets;

import com.google.common.base.Preconditions;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import io.github.thebusybiscuit.sensibletoolbox.api.filters.FilterType;
import io.github.thebusybiscuit.sensibletoolbox.api.filters.Filtering;
import io.github.thebusybiscuit.sensibletoolbox.api.gui.InventoryGUI;
import io.github.thebusybiscuit.sensibletoolbox.api.items.BaseSTBItem;

/**
 * A GUI gadget which can display and change a filter's filter type.
 *
 * @author desht
 */
public class FilterTypeGadget extends CyclerGadget<FilterType> {

    /**
     * Construct a filter type gadget.
     *
     * @param gui
     *            the GUI that the gadget belongs to
     * @param slot
     *            the GUI slot that the gadget occupies
     */
    public FilterTypeGadget(InventoryGUI gui, int slot) {
        super(gui, slot, "Filter Type");

        Preconditions.checkArgument(gui.getOwningItem() instanceof Filtering, "Filter Type gadget can only be added to filtering items!");

        add(FilterType.MATERIAL, ChatColor.GRAY, Material.STONE, FilterType.MATERIAL.getLabel());
        add(FilterType.ITEM_META, ChatColor.LIGHT_PURPLE, Material.ENCHANTED_BOOK, FilterType.ITEM_META.getLabel());
        setInitialValue(((Filtering) getGUI().getOwningItem()).getFilter().getFilterType());
    }

    @Override
    protected boolean ownerOnly() {
        return false;
    }

    @Override
    protected void apply(BaseSTBItem stbItem, FilterType newValue) {
        ((Filtering) getGUI().getOwningItem()).getFilter().setFilterType(newValue);
    }
}
