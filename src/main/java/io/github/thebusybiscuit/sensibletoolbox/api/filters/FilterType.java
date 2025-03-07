package io.github.thebusybiscuit.sensibletoolbox.api.filters;

import javax.annotation.Nonnull;

import org.bukkit.Material;

/**
 * Represents different levels of filtering precision.
 * 
 * @author desht
 * @author TheBusyBiscuit
 */
public enum FilterType {

    /**
     * Match only the item's {@link Material}.
     */
    MATERIAL("仅筛选物品类型"),

    /**
     * Match the item's {@link Material}, data byte and item meta (sometimes
     * referred to as NBT data). E.g. a diamond sword with Looting I is
     * distinguished from a diamond sword with Looting II, even if their
     * damage values are identical.
     */
    ITEM_META("筛选全部");

    private final String label;

    FilterType(@Nonnull String label) {
        this.label = label;
    }

    @Nonnull
    public String getLabel() {
        return label;
    }
}