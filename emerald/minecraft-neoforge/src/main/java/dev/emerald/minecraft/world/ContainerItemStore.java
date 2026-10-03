package dev.emerald.minecraft.world;

import dev.emerald.core.item.ItemStore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.TreeMap;

/** A real Minecraft container (chest, barrel, hopper) seen as an ItemStore. Every change is a real transfer. */
public final class ContainerItemStore implements ItemStore {
    private final Container container;

    public ContainerItemStore(Container container) {
        this.container = container;
    }

    public static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static Item itemOf(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
    }

    @Override
    public int count(String itemId) {
        Item item = itemOf(itemId);
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty() && s.is(item)) {
                total += s.getCount();
            }
        }
        return total;
    }

    @Override
    public int extract(String itemId, int amount) {
        Item item = itemOf(itemId);
        int taken = 0;
        for (int i = 0; i < container.getContainerSize() && taken < amount; i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty() && s.is(item)) {
                int n = Math.min(amount - taken, s.getCount());
                s.shrink(n);
                taken += n;
                if (s.isEmpty()) {
                    container.setItem(i, ItemStack.EMPTY);
                }
            }
        }
        if (taken > 0) {
            container.setChanged();
        }
        return taken;
    }

    @Override
    public int insert(String itemId, int amount) {
        Item item = itemOf(itemId);
        if (item == Items.AIR || amount <= 0) {
            return 0;
        }
        int left = amount;
        ItemStack probe = new ItemStack(item);
        for (int i = 0; i < container.getContainerSize() && left > 0; i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, probe) && s.getCount() < s.getMaxStackSize()) {
                int n = Math.min(left, s.getMaxStackSize() - s.getCount());
                s.grow(n);
                left -= n;
            }
        }
        for (int i = 0; i < container.getContainerSize() && left > 0; i++) {
            if (container.getItem(i).isEmpty() && container.canPlaceItem(i, probe)) {
                int n = Math.min(left, probe.getMaxStackSize());
                container.setItem(i, new ItemStack(item, n));
                left -= n;
            }
        }
        if (left < amount) {
            container.setChanged();
        }
        return amount - left;
    }

    @Override
    public Map<String, Integer> contents() {
        Map<String, Integer> out = new TreeMap<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty()) {
                out.merge(idOf(s), s.getCount(), Integer::sum);
            }
        }
        return out;
    }
}
