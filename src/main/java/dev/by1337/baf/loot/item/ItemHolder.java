package dev.by1337.baf.loot.item;

import dev.by1337.core.BCore;
import dev.by1337.item.ItemModel;
import dev.by1337.yaml.codec.DataResult;
import dev.by1337.yaml.codec.YamlCodec;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;

public class ItemHolder {
    public static final ItemHolder DIRT = new ItemHolder(Material.DIRT);
    public static final YamlCodec<ItemHolder> CODEC = YamlCodec.STRING.flatMap(
            s -> DataResult.accept(() -> new ItemHolder(s), t -> DataResult.error("Failed to decode item")),
            ItemHolder::asBase64
    );
    private byte[] bytes;
    private transient ItemStack itemStack;
    private transient ItemModel model;
    private Material defaultMaterial;

    private ItemHolder(Material material) {
        defaultMaterial = material;
    }

    public ItemHolder(ItemStack itemStack) {
        this.itemStack = itemStack.clone();
    }

    public ItemHolder(byte[] bytes) {
        this.bytes = bytes.clone();
    }

    public ItemHolder(String base64) {
        this.bytes = Base64.getDecoder().decode(base64);
    }


    public String asBase64() {
        if (bytes == null) bytes = BCore.getItemStackSerializer().serialize(getItemStack(), null);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public ItemStack getItemStack() {
        if (itemStack != null) return itemStack.clone();
        itemStack = defaultMaterial == null
                ? BCore.getItemStackSerializer().deserialize(bytes, null)
                : new ItemStack(defaultMaterial);
        return itemStack.clone();
    }

    public ItemModel itemModel() {
        if (model != null) return model;
        return model = ItemModel.fromItemStack(getItemStack());
    }
}
