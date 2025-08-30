package tech.sethi.pebbles.crates.lootcrates

import tech.sethi.pebbles.crates.util.ItemStackTypeAdapter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mojang.serialization.Dynamic
import net.minecraft.SharedConstants
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.datafix.fixes.References
import net.minecraft.world.item.ItemStack
import tech.sethi.pebbles.crates.PebblesCrate
import tech.sethi.pebbles.crates.PebblesCrate.server
import tech.sethi.pebbles.crates.util.ParseableMessage
import tech.sethi.pebbles.crates.util.ParseableName
import tech.sethi.pebbles.crates.util.setLore
import java.io.File
import kotlin.text.replace

object CrateConfigManager {
    private val gson: Gson = GsonBuilder().registerTypeAdapter(ItemStack::class.java, ItemStackTypeAdapter()).create()
    private val configDirectory = File("config/pebbles-crate/crates")
    private val crateConfigs = mutableMapOf<String, CrateConfig>()


    fun createCratesFolder() {
        if (!configDirectory.exists()) {
            configDirectory.mkdirs()
        }
    }

    init {
        loadCrateConfigs()
    }

    fun getCrateConfig(crateName: String): CrateConfig? {
        return crateConfigs[crateName]
    }

    fun saveCrateConfigs(updatedCrateConfigs: List<CrateConfig>) {
        crateConfigs.clear()
        updatedCrateConfigs.forEach { crateConfig ->
            val crateName = crateConfig.crateName
            crateConfigs[crateName] = crateConfig
            val file = File(configDirectory, "$crateName.json")
            file.writeText(gson.toJson(crateConfig))
        }
    }

    fun setCrateConfig(crateName: String, crateConfig: CrateConfig) {
        crateConfigs[crateName] = crateConfig
        saveCrateConfigs(crateConfigs.values.toList())
    }

    fun loadCrateConfigs(): MutableList<CrateConfig> {
        if (!configDirectory.exists()) {
            configDirectory.mkdirs()
        }

        val loadedConfigs = mutableListOf<CrateConfig>()

        crateConfigs.clear()

        configDirectory.listFiles { _, name -> name.endsWith(".json") }?.forEach { file ->
            val json = file.readText()
            if (json.isNotEmpty()) {
                val crateConfig = gson.fromJson(json, CrateConfig::class.java)
                val crateName = crateConfig.crateName
                crateConfigs[crateName] = crateConfig
                loadedConfigs.add(crateConfig)
            }
        }

        return loadedConfigs
    }

}

data class CrateConfig(
    val crateName: String,
    val crateKey: CrateKey,
    val screenName: String? = null,
    var prize: List<Prize>,
)

data class CrateKey(
    val material: String, val name: String, val nbt: String?, val lore: List<String>
)

data class Prize(
    val name: String,
    val material: String,
    val amount: Int,
    val nbt: String? = null,
    val commands: List<String>,
    val broadcast: String? = null,
    val messageToOpener: String? = null,
    val lore: List<String>?,
    val chance: Int
) {
    fun toStack(totalWeight: Int): ItemStack {
        var itemStack = ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(material)), amount)
        val parsedName = ParseableName(name).returnMessageAsStyledText()

        val chance = chance.toDouble() / totalWeight.toDouble() * 100
        val roundedChance = String.format("%.2f", chance)

        if (nbt != null) {
            val parsedNbt = TagParser.parseTag(nbt)

            val namespacedKeyPattern = Regex("^[a-z0-9_.-]+:[a-z0-9_/.-]+$")

            val isLegacy = parsedNbt.allKeys.any { !namespacedKeyPattern.matches(it) }
            if (isLegacy) {
                val legacyNbt = CompoundTag().apply {
                    putString("id", itemStack.itemHolder.registeredName)
                    putInt("Count", amount)
                    put("tag", parsedNbt)
                }

                val updatedNbt = server?.fixerUpper?.update(
                    References.ITEM_STACK,
                    Dynamic(PebblesCrate.nbtOps, legacyNbt),
                    3700,
                    SharedConstants.getCurrentVersion().dataVersion.version
                )?.value

                itemStack = ItemStack.CODEC.parse(PebblesCrate.nbtOps, updatedNbt).result().orElse(ItemStack.EMPTY)
            } else {
                val updatedNbt =
                    DataComponentPatch.CODEC.parse(PebblesCrate.nbtOps, TagParser.parseTag(nbt)).result()
                        .orElse(null)
                itemStack.applyComponents(updatedNbt)
                itemStack.count = amount
            }
        }

        if (lore != null) {
            val lore = lore
            val parsedPrizeLore = lore.map {
                val message = it.replace("{chance}", roundedChance)
                message.replace("{prize_name}", roundedChance)
                ParseableMessage(message, prizeName = "placeholder").returnMessageAsStyledText()
            }
            setLore(itemStack, parsedPrizeLore)
        } else {
            setLore(itemStack, listOf(Component.literal("Chance: ${roundedChance}%")))
        }

        itemStack.apply {
            set(DataComponents.CUSTOM_NAME, parsedName)
        }

        return itemStack
    }
}
