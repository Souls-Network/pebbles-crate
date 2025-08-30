package tech.sethi.pebbles.crates.screenhandlers.admin.cratelist

import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.enchantment.Enchantments
import tech.sethi.pebbles.crates.PebblesCrate
import tech.sethi.pebbles.crates.PebblesCrate.server
import tech.sethi.pebbles.crates.screenhandlers.GenericChestMenu

class ActiveCrateList(syncId: Int, val player: Player) : GenericChestMenu(MenuType.GENERIC_9x6, syncId, player.inventory, SimpleContainer(9 * 6), 6) {

    private val blacklistManager = PebblesCrate.blacklistConfigManager

    init {
        initializeInventory()
    }

    override fun stillValid(arg: Player): Boolean {
        return true
    }

    private fun initializeInventory() {
        val blacklist = blacklistManager.getBlacklist()
        val activeCrates = PebblesCrate.crateDataManager.getCrateData()
        for ((index, crateName) in activeCrates.values.withIndex()) {
            val cratePos = activeCrates.keys.elementAt(index)
            val blockOnPost = player.level().getBlockState(cratePos).block
            val crateItem = blockOnPost.asItem().defaultInstance
            crateItem.set(DataComponents.CUSTOM_NAME, crateItem.displayName.copy().append(" - $crateName"))
            if (!blacklist.contains(cratePos)) {
                val vanishingEnchant = server!!.allLevels.first().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.VANISHING_CURSE)
                crateItem.enchant(vanishingEnchant, 1)

            }
            container.setItem(index, crateItem)
        }
    }

    override fun clicked(slotIndex: Int, button: Int, actionType: ClickType, player: Player) {
        if (actionType == ClickType.THROW || actionType == ClickType.CLONE || actionType == ClickType.SWAP || actionType == ClickType.PICKUP_ALL) {
            return
        }

        val activeCrates = PebblesCrate.crateDataManager.getCrateData()
        if (slotIndex >= activeCrates.size) {
            return
        }

        val cratePos = activeCrates.keys.elementAt(slotIndex)

        val blacklist = blacklistManager.getBlacklist()
        if (blacklist.contains(cratePos)) {
            blacklistManager.removeFromBlacklist(cratePos)
        } else {
            blacklistManager.addToBlacklist(cratePos)
        }

        // close and reopen screen
        player!!.containerMenu.removed(player)
        player.openMenu(SimpleMenuProvider({ syncId, _, p ->
            ActiveCrateList(syncId, p)
        }, Component.literal("Blacklist Particles")))


        return
    }

}