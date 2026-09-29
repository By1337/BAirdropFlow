package dev.by1337.baf.command;

import dev.by1337.baf.loot.LootEntry;
import dev.by1337.baf.loot.LootTable;
import dev.by1337.baf.loot.LootTables;
import dev.by1337.baf.loot.menu.LootMenu;
import dev.by1337.bmenu.BMenu;
import dev.by1337.bmenu.menu.Menu;
import dev.by1337.cmd.Command;
import dev.by1337.cmd.CommandMsgError;
import dev.by1337.cmd.argument.ArgumentString;
import dev.by1337.core.command.bcmd.argument.ArgumentChoice;
import dev.by1337.core.command.bcmd.argument.ArgumentDouble;
import dev.by1337.core.command.bcmd.argument.ArgumentInt;
import dev.by1337.core.command.bcmd.requires.RequiresPermission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class LootCommands {
    private LootCommands() {
    }

    private static ArgumentChoice<CommandSender> tableArgument() {
        return new ArgumentChoice<>("table", () -> LootTables.DEFAULT.ids());
    }

    public static Command<CommandSender> create() {
        return new Command<CommandSender>("loot")
                .requires(new RequiresPermission<>(LootMenu.PERMISSION))
                .executor(sender -> sender.sendMessage("/baf loot list | create <id> [rolls] | edit <id> | delete <id> | rolls <id> <count> | weight <id> <entry> <weight> | save"))
                .sub(new Command<CommandSender>("list").executor(sender ->
                        sender.sendMessage("Таблицы лута: " + String.join(", ", LootTables.DEFAULT.ids()))))
                .sub(new Command<CommandSender>("create")
                        .executor(new ArgumentString<>("id"), new ArgumentInt<>("rolls"), (sender, id, rolls) -> checked(() -> {
                            if (id == null) throw new CommandMsgError("/baf loot create <id> [rolls]");
                            LootTables tables = LootTables.DEFAULT;
                            if (tables.get(id) != null) throw new CommandMsgError("Такая таблица уже существует");
                            tables.set(id, new LootTable(List.of(), rolls == null ? 1 : rolls));
                            try {
                                tables.save();
                            } catch (RuntimeException e) {
                                tables.remove(id);
                                throw e;
                            }
                            sender.sendMessage("Создана таблица " + id + ". /baf loot edit " + id);
                        })))
                .sub(new Command<CommandSender>("delete").executor(tableArgument(), (sender, id) -> checked(() -> {
                    LootTable table = requireTable(id);
                    LootTables tables = LootTables.DEFAULT;
                    tables.remove(id);
                    try {
                        tables.save();
                    } catch (RuntimeException e) {
                        tables.set(id, table);
                        throw e;
                    }
                    sender.sendMessage("Удалена таблица " + id);
                })))
                .sub(new Command<CommandSender>("edit").executor(tableArgument(), (sender, id) -> checked(() -> {
                    requireTable(id);
                    if (!(sender instanceof Player player)) throw new CommandMsgError("Меню доступно только игроку");
                    Menu menu = BMenu.menuLoader().create("baf:loot", player, null);
                    menu.addArgument("table", id);
                    player.closeInventory();
                    menu.open();
                })))
                .sub(new Command<CommandSender>("rolls").executor(tableArgument(), new ArgumentInt<>("count"), (sender, id, count) -> checked(() -> {
                    if (count == null) throw new CommandMsgError("/baf loot rolls <id> <count>");
                    LootTable table = requireTable(id);
                    LootTables.DEFAULT.requireUnlocked(table);
                    int old = table.rolls();
                    table.setRolls(count);
                    try {
                        LootTables.DEFAULT.save();
                    } catch (RuntimeException e) {
                        table.setRolls(old);
                        throw e;
                    }
                    sender.sendMessage("Прокрутки: " + count);
                })))
                .sub(new Command<CommandSender>("weight").executor(tableArgument(), new ArgumentInt<>("entry"), new ArgumentDouble<>("weight"), (sender, id, entry, weight) -> checked(() -> {
                    if (entry == null || weight == null)
                        throw new CommandMsgError("/baf loot weight <id> <entry с 1> <weight>");
                    LootTable table = requireTable(id);
                    LootTables.DEFAULT.requireUnlocked(table);
                    if (entry < 1 || entry > table.items().size())
                        throw new CommandMsgError("Предмет с таким номером отсутствует");
                    List<LootEntry> old = table.items();
                    List<LootEntry> items = new ArrayList<>(old);
                    items.set(entry - 1, new LootEntry(items.get(entry - 1).itemHolder(), weight));
                    table.setItems(items);
                    try {
                        LootTables.DEFAULT.save();
                    } catch (RuntimeException e) {
                        table.setItems(old);
                        throw e;
                    }
                    sender.sendMessage("Вес предмета " + entry + ": " + weight);
                })))
                .sub(new Command<CommandSender>("save").executor(sender -> checked(() -> {
                    Menu opened = sender instanceof Player p ? BMenu.menuLoader().getOpenedMenu(p) : null;
                    if (opened instanceof LootMenu editor) editor.save();
                    else LootTables.DEFAULT.save();
                    sender.sendMessage("Таблицы лута сохранены");
                })));
    }

    private static LootTable requireTable(String id) {
        LootTable table = LootTables.DEFAULT.get(id);
        if (table == null) throw new CommandMsgError("Укажите существующую таблицу лута");
        return table;
    }

    private static void checked(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CommandMsgError(e.getMessage());
        }
    }
}
