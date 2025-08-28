package tech.sethi.pebbles.crates.screenhandlers.admin

import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import tech.sethi.pebbles.crates.lootcrates.CrateConfig

class CrateScreenHandlerFactory(private val crateConfig: CrateConfig) : MenuProvider {
    override fun createMenu(syncId: Int, inv: Inventory, player: Player): AbstractContainerMenu {
        // Create and return the screen handler for the crate interface
        // You will implement this in the next step
        return CrateScreenHandler(syncId, inv, crateConfig)
    }

    override fun getDisplayName(): Component {
        return Component.literal(crateConfig.crateName)
    }
}

class CrateScreenHandler(
    syncId: Int,
    private val playerInventory: Inventory,
    private val crateConfig: CrateConfig
) : AbstractContainerMenu(null, syncId) {

    private val inventory: SimpleContainer

    init {
        val rows = 6
        val columns = 9
        inventory = SimpleContainer(columns * rows)

        // Populate the inventory with the prizes and their chances
        // You will implement this in the next step
        populateInventory()

        // Add the slots for the inventory
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                addSlot(Slot(inventory, column + row * columns, 8 + column * 18, 18 + row * 18))
            }
        }

        // Add the player's inventory slots
        val playerInventoryStartX = 8
        val playerInventoryStartY = 140
        val playerHotbarStartY = 198

        for (row in 0..2) {
            for (column in 0..8) {
                addSlot(
                    Slot(
                        playerInventory,
                        column + row * 9 + 9,
                        playerInventoryStartX + column * 18,
                        playerInventoryStartY + row * 18
                    )
                )
            }
        }

        for (column in 0..8) {
            addSlot(Slot(playerInventory, column, playerInventoryStartX + column * 18, playerHotbarStartY))
        }
    }

    private fun populateInventory() {
        // Populate the inventory with the prizes and their chances
        // This is just a placeholder, replace this with your actual implementation
        val prizes = crateConfig.prize
        for ((index, prize) in prizes.withIndex()) {
            // Add the prize item to the inventory with its chance as lore
            val itemStack = ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(prize.material)))
            setLore(itemStack, listOf(Component.literal("Chance: ${prize.chance}")))
            inventory.setItem(index, itemStack)
        }
    }

    override fun stillValid(arg: Player): Boolean {
        return true
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        return ItemStack.EMPTY
    }

    private fun setLore(itemStack: ItemStack, lore: List<Component>) {
        val loreComponent = ItemLore(lore)
        itemStack.set(DataComponents.LORE, loreComponent)
    }
}

