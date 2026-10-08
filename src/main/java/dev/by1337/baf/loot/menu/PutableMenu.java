package dev.by1337.baf.loot.menu;

import dev.by1337.bmenu.loader.MenuConfig;
import dev.by1337.bmenu.menu.AbstractMenu;
import dev.by1337.bmenu.menu.Menu;
import dev.by1337.bmenu.slot.SlotContent;
import dev.by1337.bmenu.slot.impl.SimpleSlotContent;
import dev.by1337.core.BCore;
import dev.by1337.item.ItemModel;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class PutableMenu extends AbstractMenu {
    private final IntSet putableSlots = new IntArraySet();
    private final SlotContent[] playerItems;
    private static final SimpleSlotContent EMPTY = new SimpleSlotContent(ItemModel.AIR);

    public PutableMenu(MenuConfig config, Player viewer, @Nullable Menu previousMenu) {
        super(config, viewer, previousMenu);
        playerItems = layers.getMatrix(3);
    }

    public void addPutableSlot(int slot) {
        putableSlots.add(slot);
        playerItems[slot] = EMPTY;
    }

    public void addPutableSlot(int... slots) {
        for (int slot : slots) {
            putableSlots.add(slot);
            playerItems[slot] = EMPTY;
        }
    }

    protected boolean isPutableSlot(int slot) {
        return putableSlots.contains(slot);
    }

    protected void hasMutableClick() {

    }

    public void clearPutableSlots() {
        var inv = getInventory();
        var it = putableSlots.intIterator();
        while (it.hasNext()) {
            inv.setItem(it.nextInt(), null);
        }
        BCore.getInventoryUtil().flushInv(viewer);
    }

    public List<ItemStack> collectItems() {
        var inv = getInventory();
        List<ItemStack> result = new ArrayList<>();
        var it = putableSlots.intIterator();
        while (it.hasNext()) {
            var item = inv.getItem(it.nextInt());
            if (item != null && !item.getType().isAir()) {
                result.add(item);
            }
        }
        return result;
    }

    public void removeIf(Predicate<ItemStack> tester) {
        var inv = getInventory();
        var it = putableSlots.intIterator();
        while (it.hasNext()) {
            int slot = it.nextInt();
            var item = inv.getItem(slot);
            if (item != null && !item.getType().isAir() && tester.test(item)) {
                inv.setItem(slot, null);
            }
        }
    }

    public void replaceAll(Function<ItemStack, @Nullable ItemStack> map) {
        var inv = getInventory();
        var it = putableSlots.intIterator();
        while (it.hasNext()) {
            int slot = it.nextInt();
            var item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                inv.setItem(slot, map.apply(item));
            }
        }
    }

    public void forEach(Consumer<ItemStack> map) {
        var inv = getInventory();
        var it = putableSlots.intIterator();
        while (it.hasNext()) {
            int slot = it.nextInt();
            var item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                map.accept(item);
            }
        }
    }

    @Override
    public void onClick(InventoryDragEvent e) {
        int size = getInventory().getSize();
        if (e.getRawSlots().stream().allMatch(i -> i >= size || putableSlots.contains(i.intValue()))) {
            hasMutableClick();
            e.setCancelled(false);
            return;
        }
        super.onClick(e);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (e.getClickedInventory() != getInventory() || putableSlots.contains(e.getSlot())) {
            hasMutableClick();
            e.setCancelled(false);
            return;
        }
        super.onClick(e);
    }
}
