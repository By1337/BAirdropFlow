package dev.by1337.baf.loot.menu;

import dev.by1337.baf.BAF;
import dev.by1337.baf.loot.LootEntry;
import dev.by1337.baf.loot.LootTable;
import dev.by1337.baf.loot.LootTables;
import dev.by1337.baf.loot.item.ItemHolder;
import dev.by1337.bmenu.command.ExecuteContext;
import dev.by1337.bmenu.factory.MenuCodecs;
import dev.by1337.bmenu.loader.MenuConfig;
import dev.by1337.bmenu.menu.Menu;
import dev.by1337.bmenu.menu.command.MenuCommands;
import dev.by1337.cmd.Command;
import dev.by1337.cmd.CommandMsgError;
import dev.by1337.core.command.bcmd.argument.ArgumentInt;
import dev.by1337.core.util.text.minimessage.MiniMessage;
import dev.by1337.core.BCore;
import dev.by1337.plc.PlaceholderResolver;
import dev.by1337.plc.Placeholders;
import dev.by1337.yaml.codec.PipelineYamlCodecBuilder;
import dev.by1337.yaml.codec.YamlCodec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.IntStream;

public class LootMenu extends PutableMenu {
    public static final String PERMISSION = "baf.loot.admin";
    private static final NamespacedKey WEIGHT = new NamespacedKey("baf", "loot_weight");
    private static final NamespacedKey MIN_COUNT = new NamespacedKey("baf", "loot_min_count");
    private static final NamespacedKey MAX_COUNT = new NamespacedKey("baf", "loot_max_count");
    private enum EditMode {
        ITEMS("Предметы"), WEIGHTS("Веса"), COUNTS("Количество");
        private final String title;
        EditMode(String title) { this.title = title; }
    }
    private static final PlaceholderResolver<LootMenu> PLACEHOLDERS = Placeholders.<LootMenu>create()
            .withContext("current_page", v -> v.currentPage + 1)
            .withContext("max_page", v -> Math.max(v.currentPage, v.draft.maxPage()) + 1)
            .withContext("has_next_page", v -> v.currentPage < v.draft.maxPage())
            .withContext("loot_table_id", v -> v.tableId)
            .withContext("rolls", v -> v.rolls)
            .withContext("weight_mode", v -> v.mode == EditMode.WEIGHTS)
            .withContext("count_mode", v -> v.mode == EditMode.COUNTS)
            .withContext("edit_mode", v -> v.mode.title);
    private static final Command<ExecuteContext> COMMANDS = new Command<ExecuteContext>("root")
            .and(MenuCommands.getCommands())
            .sub(new Command<ExecuteContext>("[next_page]").executor(ctx -> {
                if (ctx.menu instanceof LootMenu m) m.changePage(1);
            }))
            .sub(new Command<ExecuteContext>("[previous_page]").executor(ctx -> {
                if (ctx.menu instanceof LootMenu m) m.changePage(-1);
            }))
            .sub(new Command<ExecuteContext>("[toggle_weights]").executor(ctx -> {
                if (ctx.menu instanceof LootMenu m) {
                    m.capturePage();
                    m.mode = EditMode.values()[(m.mode.ordinal() + 1) % EditMode.values().length];
                    m.render();
                }
            }))
            .sub(new Command<ExecuteContext>("[save_loot]").executor(ctx -> {
                if (ctx.menu instanceof LootMenu m) {
                    m.save();
                    m.viewer.sendMessage("Таблица лута сохранена");
                }
            }))
            .sub(new Command<ExecuteContext>("[rolls]").executor(new ArgumentInt<>("delta"), (ctx, delta) -> {
                if (ctx.menu instanceof LootMenu m && delta != null) {
                    long value = (long) m.rolls + delta;
                    if (value < 0 || value > Integer.MAX_VALUE) return;
                    m.rolls = (int) value;
                    m.save();
                    m.refresh();
                }
            }));

    private final Config cfg;
    private boolean slotsReady;
    private LootTable table;
    private String tableId;
    private LootEditorDraft draft;
    private int currentPage;
    private int rolls;
    private EditMode mode = EditMode.ITEMS;
    private boolean closed = true;
    private boolean updateScheduled;

    public LootMenu(Config config, Player viewer, @Nullable Menu previousMenu) {
        super(config, viewer, previousMenu);
        addPlaceholderResolver(PLACEHOLDERS.bindCtx(this));
        cfg = config;
        if (config.itemSlots.length == 0 || config.animation() != null) {
            throw new IllegalArgumentException("Редактору нужны item_slots без анимации");
        }
        for (int slot : config.itemSlots) {
            if (slot < 0 || slot >= menuSize() || isPutableSlot(slot)) {
                throw new IllegalArgumentException("Некорректный item_slots: " + slot);
            }
            addPutableSlot(slot);
        }
    }

    @Override
    public void open(boolean isReopen) {
        if (!viewer.hasPermission(PERMISSION)) throw new CommandMsgError("Нет права " + PERMISSION);
        if (!closed) throw new CommandMsgError("Сначала закройте текущий редактор");
        tableId = args.get("table");
        table = LootTables.DEFAULT.get(tableId);
        if (table == null) throw new CommandMsgError("Неизвестная таблица лута: " + tableId);
        LootTables.DEFAULT.beginEdit(table, this);
        draft = new LootEditorDraft(table.items(), cfg.itemSlots.length);
        rolls = table.rolls();
        currentPage = 0;
        mode = EditMode.ITEMS;
        slotsReady = false;
        closed = false;
        try {
            super.open(isReopen);
            // Let BMenu finish the first AIR render before filling the inventory.
            // Later refreshes keep the same PutableMenu markers and do not touch these slots.
            if (isOpened()) {
                new BukkitRunnable() {
                    @Override public void run() {
                        if (closed || !isOpened()) { cancel(); return; }
                        for (int slot : cfg.itemSlots) {
                            ItemStack item = getInventory().getItem(slot);
                            if (item != null && !item.getType().isAir()) return;
                        }
                        slotsReady = true;
                        cancel();
                        render();
                    }
                }.runTaskTimer(BAF.plugin(), 1, 1);
            }
            if (!isOpened()) {
                closed = true;
                LootTables.DEFAULT.endEdit(table, this);
            }
        } catch (RuntimeException e) {
            closed = true;
            LootTables.DEFAULT.endEdit(table, this);
            throw e;
        }
    }

    @Override
    protected void generate() {
        for (int slot = 0; slot < menuSize(); slot++) {
            if (isPutableSlot(slot)) continue;
            var content = layers.getItemInSlot(slot);
            if (content != null) {
                LootMenuText.restore(content.getItemModel());
                content.setDirty(true);
            }
        }
        List<LootEntry> page = draft.page(currentPage);
        if (!slotsReady) {
            // A temporary sentinel tells us when each initial AIR write has completed.
            for (int slot : cfg.itemSlots) getInventory().setItem(slot, new ItemStack(Material.BARRIER));
            return;
        }
        for (int i = 0; i < cfg.itemSlots.length; i++) {
            LootEntry entry = page.get(i);
            ItemStack stack = entry == null ? null : entry.itemHolder().getItemStack();
            if (stack != null) {
                stack.editMeta(meta -> {
                    meta.getPersistentDataContainer().set(WEIGHT, PersistentDataType.DOUBLE, entry.weight());
                    meta.getPersistentDataContainer().set(MIN_COUNT, PersistentDataType.INTEGER, entry.minCount());
                    meta.getPersistentDataContainer().set(MAX_COUNT, PersistentDataType.INTEGER, entry.maxCount());
                    if (mode != EditMode.ITEMS) {
                        List<Component> lore = new ArrayList<>(Objects.requireNonNullElse(meta.lore(), List.of()));
                        for (String line : mode == EditMode.WEIGHTS ? cfg.weightLore : cfg.countLore) {
                            lore.add(MiniMessage.deserialize(setPlaceholders(entry.<Menu>placeholders().setPlaceholders(line, this))).decoration(TextDecoration.ITALIC, false));
                        }
                        meta.lore(lore);
                    }
                });
            }
            getInventory().setItem(cfg.itemSlots[i], stack);
        }
        BCore.getInventoryUtil().flushInv(viewer);
    }

    private void capturePage() {
        if (closed || !slotsReady || mode != EditMode.ITEMS) return;
        List<LootEntry> page = new ArrayList<>(cfg.itemSlots.length);
        for (int slot : cfg.itemSlots) {
            ItemStack stack = getInventory().getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                page.add(null);
                continue;
            }
            Double storedWeight = stack.getItemMeta().getPersistentDataContainer().get(WEIGHT, PersistentDataType.DOUBLE);
            double weight = storedWeight != null && Double.isFinite(storedWeight) && storedWeight >= 0 ? storedWeight : 100;
            ItemStack clean = stack.clone();
            var data = stack.getItemMeta().getPersistentDataContainer();
            int min = data.getOrDefault(MIN_COUNT, PersistentDataType.INTEGER, 0);
            int max = data.getOrDefault(MAX_COUNT, PersistentDataType.INTEGER, 0);
            clean.editMeta(meta -> {
                var cleanData = meta.getPersistentDataContainer();
                cleanData.remove(WEIGHT);
                cleanData.remove(MIN_COUNT);
                cleanData.remove(MAX_COUNT);
            });
            page.add(new LootEntry(new ItemHolder(clean), weight, min, max));
        }
        draft.replacePage(currentPage, page);
    }

    @Override
    public void refresh() {
        capturePage();
        super.refresh();
    }

    private void render() {
        super.refresh();
    }

    public void save() {
        capturePage();
        table.setItems(draft.items());
        table.setRolls(rolls);
        // Keep edits in memory even if saving fails, so a later save can recover them.
        try {
            LootTables.DEFAULT.save();
        } catch (RuntimeException e) {
            viewer.sendMessage("Не удалось записать лут на диск. Изменения остались в памяти; повторите сохранение кнопкой меню.");
            throw e;
        }
    }

    private void changePage(int delta) {
        capturePage();
        int next = currentPage + delta;
        if (next < 0 || next > draft.maxPage()) return;
        save();
        currentPage = next;
        super.refresh();
    }

    @Override
    protected void hasMutableClick() {
        if (updateScheduled) return;
        updateScheduled = true;
        Bukkit.getScheduler().runTask(BAF.plugin(), () -> {
            updateScheduled = false;
            if (closed || !isOpened()) return;
            refresh();
        });
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (closed || !slotsReady || !viewer.hasPermission(PERMISSION)) return;
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < menuSize() && isPutableSlot(slot)) {
            if (mode != EditMode.ITEMS) {
                if (!event.isLeftClick() && !event.isRightClick()) return;
                int offset = -1;
                for (int i = 0; i < cfg.itemSlots.length; i++) if (cfg.itemSlots[i] == slot) offset = i;
                List<LootEntry> page = draft.page(currentPage);
                LootEntry entry = page.get(offset);
                if (entry == null) return;
                if (mode == EditMode.WEIGHTS) {
                    double delta = event.isShiftClick() ? 10 : 1;
                    double weight = Math.max(0, entry.weight() + (event.isLeftClick() ? delta : -delta));
                    if (!Double.isFinite(weight)) return;
                    page.set(offset, new LootEntry(entry.itemHolder(), weight, entry.minCount(), entry.maxCount()));
                } else {
                    int min = entry.minCount();
                    int max = entry.maxCount();
                    int delta = event.isLeftClick() ? 1 : -1;
                    if (min == 0 || max == 0) {
                        if (delta < 0) return;
                        min = max = Math.max(1, entry.itemHolder().getItemStack().getAmount());
                    } else if (event.isShiftClick()) {
                        max = (int) Math.clamp((long) max + delta, 0, Integer.MAX_VALUE);
                        min = Math.min(min, max);
                    } else {
                        min = (int) Math.clamp((long) min + delta, 0, Integer.MAX_VALUE);
                        max = Math.max(min, max);
                    }
                    if (min == 0 || max == 0) min = max = 0;
                    page.set(offset, new LootEntry(entry.itemHolder(), entry.weight(), min, max));
                }
                draft.replacePage(currentPage, page);
                save();
                super.refresh();
                return;
            }
            if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) return;
            super.onClick(event);
            return;
        }
        if (slot >= menuSize() || slot == -999) {
            // Shift transfer and double click can affect the toolbar, so handle shift insertion explicitly.
            if (mode != EditMode.ITEMS || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) return;
            if (event.isShiftClick()) {
                ItemStack item = event.getCurrentItem();
                if (item == null || item.getType().isAir()) return;
                for (int target : cfg.itemSlots) {
                    ItemStack old = getInventory().getItem(target);
                    if (old == null || old.getType().isAir()) {
                        getInventory().setItem(target, item.clone());
                        event.setCurrentItem(null);
                        hasMutableClick();
                        break;
                    }
                }
                return;
            }
            super.onClick(event);
            return;
        }
        super.onClick(event);
    }

    @Override
    public void onClick(InventoryDragEvent event) {
        event.setCancelled(true);
        if (closed || !slotsReady || mode != EditMode.ITEMS || !viewer.hasPermission(PERMISSION)) return;
        super.onClick(event);
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        if (closed) return;
        try {
            save();
        } catch (Exception e) {
            BAF.plugin().getSLF4JLogger().error("Не удалось сохранить таблицу {}", tableId, e);
        } finally {
            closed = true;
            LootTables.DEFAULT.endEdit(table, this);
            super.onClose(event);
        }
    }

    @Override
    public Command<ExecuteContext> getCommands() {
        return COMMANDS;
    }

    public static class Config extends MenuConfig {
        public static final YamlCodec<Config> CODEC = PipelineYamlCodecBuilder.of(Config::new)
                .and(MenuConfig.RAW_CODEC)
                .field(MenuCodecs.SLOTS_CODEC, "item_slots", v -> v.itemSlots, (m, v) -> m.itemSlots = v,
                        IntStream.range(0, 45).toArray())
                .strings("weight_lore", v -> v.weightLore, (m, v) -> m.weightLore = v, List.of("<gold>Вес: {weight}"))
                .strings("count_lore", v -> v.countLore, (m, v) -> m.countLore = v, List.of(
                        "<gold>Количество: {min_count}–{max_count}",
                        "<gray>ЛКМ/ПКМ: минимум +1/-1; Shift: максимум +1/-1",
                        "<gray>0/0: исходный стак; ЛКМ включает диапазон"))
                .build();
        public int[] itemSlots = IntStream.range(0, 45).toArray();
        public List<String> weightLore = List.of("<gold>Вес: {weight}");

        public List<String> countLore = List.of(
                "<gold>Количество: {min_count}–{max_count}",
                "<gray>ЛКМ/ПКМ: минимум +1/-1; Shift: максимум +1/-1",
                "<gray>0/0: исходный стак; ЛКМ включает диапазон");

        @Override
        public Menu create(Player viewer, @Nullable Menu previousMenu) {
            return new LootMenu(this, viewer, previousMenu);
        }
    }
}
