package com.servidor.basicenchantments;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BasicEnchantments extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    // 1. Bloquea el lapislázuli común
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getType() == InventoryType.ENCHANTING) {
            if (event.getRawSlot() == 1 || (event.isShiftClick() && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.LAPIS_LAZULI)) {
                ItemStack item = event.getCursor();
                if (event.isShiftClick()) item = event.getCurrentItem();

                if (item != null && item.getType() == Material.LAPIS_LAZULI) {
                    if (!isSpecialLapis(item)) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player) {
                            ((Player) event.getWhoClicked()).sendMessage("§cSolo puedes usar Lapislázuli Especial (CustomModelData: 1).");
                        }
                    }
                }
            }
        }
    }

    // 2. Fuerza que los 3 botones de la mesa siempre estén encendidos
    @EventHandler
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        Enchantment dummy = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
        EnchantmentOffer[] offers = event.getOffers();
        
        for (int i = 0; i < 3; i++) {
            int cost = i + 1; // Niveles 1, 2 y 3
            if (offers[i] == null) {
                if (dummy != null) {
                    offers[i] = new EnchantmentOffer(dummy, 1, cost);
                }
            } else {
                offers[i].setCost(cost);
            }
        }
    }

    // 3. Procesa el encantamiento y descuenta los recursos
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnchantItem(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        EnchantingInventory inv = (EnchantingInventory) event.getInventory();
        ItemStack lapisSlot = inv.getSecondary();

        int expCost = event.getExpLevelCost();
        // Nivel 1 = 2 Lapis | Nivel 2 = 4 Lapis | Nivel 3 = 6 Lapis
        int reqLapis = (expCost == 1) ? 2 : (expCost == 2) ? 4 : 6;

        if (!hasEnoughSpecialLapis(lapisSlot, player, reqLapis)) {
            event.setCancelled(true);
            player.sendMessage("§cNecesitas §b" + reqLapis + "x Lapislázuli Especial §cpara esta opción.");
            return;
        }

        ItemStack targetItem = event.getItem();
        Enchantment chosenEnchant = getRandomValidEnchantment(targetItem);

        if (chosenEnchant == null) {
            event.setCancelled(true);
            player.sendMessage("§cEste objeto no tiene encantamientos compatibles disponibles.");
            return;
        }

        consumeSpecialLapis(inv, player, reqLapis);

        event.getEnchantsToAdd().clear();
        event.getEnchantsToAdd().put(chosenEnchant, expCost); // Aplica nivel 1, 2 o 3 según el botón

        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
    }

    private boolean isSpecialLapis(ItemStack item) {
        if (item == null || item.getType() != Material.LAPIS_LAZULI) return false;
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == 1;
    }

    private boolean hasEnoughSpecialLapis(ItemStack inSlot, Player player, int required) {
        int count = 0;
        if (isSpecialLapis(inSlot)) {
            count += inSlot.getAmount();
        }
        if (count >= required) return true;

        for (ItemStack item : player.getInventory().getContents()) {
            if (isSpecialLapis(item)) {
                count += item.getAmount();
            }
        }
        return count >= required;
    }

    private void consumeSpecialLapis(EnchantingInventory inv, Player player, int amount) {
        int remaining = amount;
        ItemStack inSlot = inv.getSecondary();

        if (isSpecialLapis(inSlot)) {
            if (inSlot.getAmount() <= remaining) {
                remaining -= inSlot.getAmount();
                inv.setSecondary(null);
            } else {
                inSlot.setAmount(inSlot.getAmount() - remaining);
                remaining = 0;
            }
        }

        if (remaining > 0) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (isSpecialLapis(item)) {
                    if (item.getAmount() <= remaining) {
                        remaining -= item.getAmount();
                        item.setAmount(0);
                    } else {
                        item.setAmount(item.getAmount() - remaining);
                        remaining = 0;
                    }
                    if (remaining <= 0) break;
                }
            }
        }
    }

    private Enchantment getRandomValidEnchantment(ItemStack item) {
        String[] keys = {
            "protection", "fire_protection", "feather_falling", "blast_protection",
            "projectile_protection", "respiration", "aqua_affinity", "thorns",
            "depth_strider", "frost_walker", "sharpness", "smite", "bane_of_arthropods",
            "knockback", "fire_aspect", "looting", "sweeping", "efficiency",
            "silk_touch", "unbreaking", "fortune", "power", "punch", "flame",
            "infinity", "luck_of_the_sea", "lure", "loyalty", "impaling",
            "riptide", "channeling", "multishot", "quick_charge", "piercing",
            "mending"
        };

        List<Enchantment> valid = new ArrayList<>();
        for (String k : keys) {
            Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(k));
            if (ench != null && ench.canEnchantItem(item)) {
                valid.add(ench);
            }
        }

        if (valid.isEmpty()) return null;
        Collections.shuffle(valid);
        return valid.get(0);
    }
}
