package io.github.thebusybiscuit.sensibletoolbox.items.itemroutermodules;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapelessRecipe;

import io.github.thebusybiscuit.sensibletoolbox.api.STBInventoryHolder;
import io.github.thebusybiscuit.sensibletoolbox.api.SensibleToolbox;
import io.github.thebusybiscuit.sensibletoolbox.api.items.BaseSTBBlock;

public class DistributorModule extends DirectionalItemRouterModule {

    private int nextNeighbour = 0;

    public DistributorModule() {}

    public DistributorModule(ConfigurationSection conf) {
        super(conf);
    }

    @Override
    public Material getMaterial() {
        return Material.RED_DYE;
    }

    @Override
    public String getItemName() {
        return "中转站升级";
    }

    @Override
    public String[] getLore() {
        return new String[] { "从配置的方向中取出物品，并依次发送到相邻的机器中" };
    }

    @Override
    public Recipe getMainRecipe() {
        BlankModule bm = new BlankModule();
        registerCustomIngredients(bm);
        ShapelessRecipe recipe = new ShapelessRecipe(getKey(), toItemStack());
        recipe.addIngredient(bm.getMaterial());
        recipe.addIngredient(Material.STICKY_PISTON);
        recipe.addIngredient(Material.ARROW);
        return recipe;
    }

    @Override
    public boolean execute(Location l) {
        if (getItemRouter() == null) {
            // shouldn't happen...
            return false;
        }

        doPull(getFacing(), l);

        if (getItemRouter().getNeighbours().size() > 1 && getItemRouter().getBufferItem() != null) {
            int nToInsert = getItemRouter().getStackSize();
            BlockFace face = getNextNeighbour();

            if (face == getFacing()) {
                // shouldn't happen...
                return false;
            }

            Block b = l.getBlock();
            Block target = b.getRelative(face);
            BaseSTBBlock stb = SensibleToolbox.getBlockAt(target.getLocation(), true);

            if (stb instanceof STBInventoryHolder) {
                ItemStack toInsert = getItemRouter().getBufferItem().clone();
                toInsert.setAmount(Math.min(nToInsert, toInsert.getAmount()));
                int nInserted = ((STBInventoryHolder) stb).insertItems(toInsert, face.getOppositeFace(), false, getItemRouter().getOwner());
                getItemRouter().reduceBuffer(nInserted);
                return nInserted > 0;
            } else {
                // vanilla inventory holder?
                return vanillaInsertion(target, nToInsert, getFacing().getOppositeFace());
            }
        }

        return false;
    }

    private BlockFace getNextNeighbour() {
        List<BlockFace> neighbours = getItemRouter().getNeighbours();
        nextNeighbour = (nextNeighbour + 1) % neighbours.size();

        if (neighbours.get(nextNeighbour) == getFacing()) {
            nextNeighbour = (nextNeighbour + 1) % neighbours.size();
        }

        return neighbours.get(nextNeighbour);
    }
}
