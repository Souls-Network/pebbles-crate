package tech.sethi.pebbles.crates.screenhandlers

import net.minecraft.world.Container
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

open class GenericChestMenu(
    generic9x6: MenuType<ChestMenu>,
    syncId: Int,
    inv: Inventory,
    crateInventory: Container,
    i2: Int,
) : ChestMenu(generic9x6, syncId, inv, crateInventory, i2) {
    // Disables shift-click transfers (both directions)
    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    // Disables double-click “collect to cursor”
    override fun canTakeItemForPickAll(stack: ItemStack, slot: Slot): Boolean = false

}