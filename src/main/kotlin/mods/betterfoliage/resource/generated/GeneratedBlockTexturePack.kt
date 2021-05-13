package mods.betterfoliage.resource.generated

import mods.betterfoliage.util.Atlas
import mods.betterfoliage.util.HasLogger
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
import net.minecraft.client.resource.ClientResourcePackProfile
import net.minecraft.resource.*
import net.minecraft.resource.ResourceType.CLIENT_RESOURCES
import net.minecraft.resource.metadata.ResourceMetadataReader
import net.minecraft.text.LiteralText
import net.minecraft.util.Identifier
import net.minecraft.util.profiler.Profiler
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Level.INFO
import org.apache.logging.log4j.Logger
import java.io.IOException
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * [ResourcePack] containing generated block textures
 *
 * @param[reloadId] Fabric ID of the pack
 * @param[nameSpace] Resource namespace of pack
 * @param[packName] Friendly name of pack
 * @param[packDesc] Description of pack
 * @param[logger] Logger to log to when generating resources
 */
class GeneratedBlockTexturePack(
    val reloadId: Identifier, val nameSpace: String, val packName: String, val packDesc: String
) : HasLogger(), ResourcePack {

    override fun getName() = reloadId.toString()
    override fun getNamespaces(type: ResourceType) = setOf(nameSpace)
    override fun <T : Any?> parseMetadata(deserializer: ResourceMetadataReader<T>) = null
    override fun openRoot(id: String) = null
    override fun findResources(type: ResourceType, path: String, prefix: String, maxDepth: Int, filter: Predicate<String>) = emptyList<Identifier>()

    override fun close() {}

    protected var manager: ResourceManager? = null
    val identifiers: MutableMap<Any, Identifier> = Collections.synchronizedMap(mutableMapOf<Any, Identifier>())
    val resources: MutableMap<Identifier, ByteArray> = Collections.synchronizedMap(mutableMapOf<Identifier, ByteArray>())

    fun register(key: Any, func: (ResourceManager)->ByteArray): Identifier {
        if (manager == null) throw IllegalStateException("Cannot register resources unless resource manager is being reloaded")
        identifiers[key]?.let { return it }

        val id = Identifier(nameSpace, UUID.randomUUID().toString())
        val resource = func(manager!!)

        identifiers[key] = id
        resources[Atlas.BLOCKS.file(id)] = resource
        detailLogger.log(INFO, "generated resource $key -> $id")
        return id
    }

    override fun open(type: ResourceType, id: Identifier) =
        if (type != CLIENT_RESOURCES) null else
            try { resources[id]!!.inputStream() }
            catch (e: ExecutionException) { (e.cause as? IOException)?.let { throw it } }   // rethrow wrapped IOException if present

    override fun contains(type: ResourceType, id: Identifier) =
        type == CLIENT_RESOURCES && resources.containsKey(id)

    /**
     * Supplier for this resource pack. Adds pack as always-on and hidden.
     */
    val finder = object : ResourcePackProvider {
        val packInfo = ClientResourcePackProfile(
            packName, true, Supplier { this@GeneratedBlockTexturePack },
            LiteralText(packName),
            LiteralText(packDesc),
            ResourcePackCompatibility.COMPATIBLE, ResourcePackProfile.InsertionPosition.TOP, true, null
        )

        override fun <T : ResourcePackProfile> register(
            registry: MutableMap<String, T>,
            factory: ResourcePackProfile.Factory<T>
        ) {
            (registry as MutableMap<String, ResourcePackProfile>)[reloadId.toString()] = packInfo
        }
    }

    val reloader = object : IdentifiableResourceReloadListener {
        override fun getFabricId() = reloadId

        override fun reload(synchronizer: ResourceReloadListener.Synchronizer, manager: ResourceManager, prepareProfiler: Profiler, applyProfiler: Profiler, prepareExecutor: Executor, applyExecutor: Executor): CompletableFuture<Void> {
            this@GeneratedBlockTexturePack.manager = manager
            return synchronizer.whenPrepared(null).thenRun {
                this@GeneratedBlockTexturePack.manager = null
                identifiers.clear()
                resources.clear()
            }
        }
    }
}