package mods.betterfoliage.model

import mods.betterfoliage.WeightedBakedModelEntry_model
import mods.betterfoliage.WeightedBakedModel_models
import mods.betterfoliage.WeightedBakedModel_totalWeight
import mods.betterfoliage.WeightedPickerEntry_weight
import mods.betterfoliage.util.get
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext
import net.minecraft.block.BlockState
import net.minecraft.client.render.RenderLayers
import net.minecraft.client.render.model.BakedModel
import net.minecraft.client.render.model.BasicBakedModel
import net.minecraft.client.render.model.WeightedBakedModel
import net.minecraft.item.ItemStack
import net.minecraft.util.WeightedPicker
import net.minecraft.util.math.BlockPos
import net.minecraft.world.BlockRenderView
import java.util.*
import java.util.function.Supplier

abstract class WrappedBakedModel(val wrapped: BakedModel) : BakedModel by wrapped, FabricBakedModel {
    override fun isVanillaAdapter() = false

    override fun emitItemQuads(stack: ItemStack, randomSupplier: Supplier<Random>, context: RenderContext) {
        (wrapped as FabricBakedModel).emitItemQuads(stack, randomSupplier, context)
    }

    override fun emitBlockQuads(blockView: BlockRenderView, state: BlockState, pos: BlockPos, randomSupplier: Supplier<Random>, context: RenderContext) {
        (wrapped as FabricBakedModel).emitBlockQuads(blockView, state, pos, randomSupplier, context)
    }
}

class WrappedMeshModel(wrapped: BasicBakedModel, val mesh: Mesh) : WrappedBakedModel(wrapped) {
    override fun emitBlockQuads(blockView: BlockRenderView, state: BlockState, pos: BlockPos, randomSupplier: Supplier<Random>, context: RenderContext) {
        context.meshConsumer().accept(mesh)
    }

    companion object {
        /**
         * Converter for [BasicBakedModel] instances.
         * @param state [BlockState] to use when querying [BakedModel]
         * @param unshade undo vanilla diffuse lighting when unbaking the [BakedModel]
         * @param noDiffuse disable diffuse lighting when baking the [Mesh]
         * @param blendModeOverride [BlockRenderLayer] to use instead of the one declared by the corresponding [Block]
         */
        fun converter(state: BlockState, unshade: Boolean = false, noDiffuse: Boolean = true, blendModeOverride: BlendMode? = null) = BakedModelConverter.of { model, _ ->
            if (model is BasicBakedModel) {
                val mesh = unbakeQuads(model, state, Random(42L), unshade).build(
                    blendMode = blendModeOverride ?: BlendMode.fromRenderLayer(RenderLayers.getBlockLayer(state)),
                    noDiffuse = noDiffuse,
                    flatLighting = !model.useAmbientOcclusion()
                )
                WrappedMeshModel(model, mesh)
            } else null
        }
    }
}

class WrappedWeightedModel(wrapped: WeightedBakedModel, transformer: BakedModelConverter) : WrappedBakedModel(wrapped) {
    val totalWeight = wrapped[WeightedBakedModel_totalWeight] as Int
    val models = wrapped[WeightedBakedModel_models]!!.map { entry ->
        Entry(transformer.convert(entry[WeightedBakedModelEntry_model]!!, transformer)!!, entry[WeightedPickerEntry_weight]!!)
    }

    override fun emitBlockQuads(blockView: BlockRenderView, state: BlockState, pos: BlockPos, randomSupplier: Supplier<Random>, context: RenderContext) {
        (WeightedPicker.getRandom(randomSupplier.get(), models, totalWeight).model as FabricBakedModel).emitBlockQuads(blockView, state, pos, randomSupplier, context)
    }

    class Entry(val model: BakedModel, weight: Int) : WeightedPicker.Entry(weight)

    companion object {
        val converter = object : BakedModelConverter {
            override fun convert(model: BakedModel, converter: BakedModelConverter) =
                (model as? WeightedBakedModel)?.let { WrappedWeightedModel(it, converter) }
        }
    }
}

