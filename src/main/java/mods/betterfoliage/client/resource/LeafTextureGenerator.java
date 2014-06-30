package mods.betterfoliage.client.resource;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mods.betterfoliage.BetterFoliage;
import mods.betterfoliage.client.BetterFoliageClient;
import mods.betterfoliage.common.util.DeobfNames;
import mods.betterfoliage.common.util.ReflectionUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Generates rounded crossleaf textures for all registered normal leaf textures at stitch time.
 * @author octarine-noise
 */
@SideOnly(Side.CLIENT)
public class LeafTextureGenerator implements IIconRegister, IResourceManager {

	/** Resource domain name of autogenerated crossleaf textures */
	public String domainName = "bf_leaves_autogen";
	
	/** Resource location for fallback texture (if the generation process fails) */
	public ResourceLocation missing_resource = new ResourceLocation("betterfoliage", "textures/blocks/missingleaf.png");
	
	/** Texture atlas for block textures used in the current run */
	public TextureMap blockTextures;
	
	/** List of helpers which can identify leaf textures loaded by alternate means */
	public List<ILeafTextureRecognizer> recognizers = Lists.newLinkedList();
	
	/** Number of textures generated in the current run */
	int counter = 0;
	
	public Set<String> getResourceDomains() {
		return ImmutableSet.<String>of(domainName);
	}

	public IResource getResource(ResourceLocation resourceLocation) throws IOException {
		// remove "/blocks/textures/" from beginning
		String origResPath = resourceLocation.getResourcePath().substring(16);
		LeafTextureResource result = new LeafTextureResource(new ResourceLocation(origResPath));
		if (result.data == null) {
			return Minecraft.getMinecraft().getResourceManager().getResource(missing_resource);
		} else {
			counter++;
			return result;
		}
	}

	public List<IResource> getAllResources(ResourceLocation resource) throws IOException {
		return ImmutableList.<IResource>of();
	}

	/** Leaf blocks register their textures here. An extra texture will be registered in the atlas
	 *  for each, with the resource domain of this generator.
	 *  @return the originally registered {@link IIcon} already in the atlas
	 */
	public IIcon registerIcon(String resourceLocation) {
		IIcon original = blockTextures.getTextureExtry(resourceLocation);
		blockTextures.registerIcon(new ResourceLocation(domainName, resourceLocation).toString());
		BetterFoliage.log.debug(String.format("Found leaf texture: %s", resourceLocation));
		return original;
	}

	/** Iterates through all leaf blocks in the registry and makes them register
	 *  their textures to "sniff out" all leaf textures.
	 * @param event
	 */
	@SuppressWarnings("unchecked")
	@SubscribeEvent
	public void handleTextureReload(TextureStitchEvent.Pre event) {
		if (event.map.getTextureType() != 0) return;
		
		blockTextures = event.map;
		counter = 0;
		BetterFoliage.log.info("Reloading leaf textures");
		
		Map<String, IResourceManager> domainManagers = ReflectionUtil.getDomainResourceManagers();
		if (domainManagers == null) {
			BetterFoliage.log.warn("Failed to inject leaf texture generator");
			return;
		}
		domainManagers.put(domainName, this);
		
		// register simple block textures
		Iterator<Block> iter = Block.blockRegistry.iterator();
		while(iter.hasNext()) {
			Block block = iter.next();
			for (Class<?> clazz : BetterFoliageClient.blockLeavesClasses) if (clazz.isAssignableFrom(block.getClass())) {
				BetterFoliage.log.debug(String.format("Inspecting leaf block: %s", block.getClass().getName()));
				block.registerBlockIcons(this);
			}
		}
		
		// enumerate all registered textures, find leaf textures among them
		Map<String, TextureAtlasSprite> mapAtlas = null;
		mapAtlas = ReflectionUtil.getField(blockTextures, DeobfNames.TM_MRS_SRG, Map.class);
		if (mapAtlas == null) mapAtlas = ReflectionUtil.getField(blockTextures, DeobfNames.TM_MRS_MCP, Map.class);
		if (mapAtlas == null) {
			BetterFoliage.log.warn("Failed to reflect texture atlas, textures may be missing");
		} else {
			Set<String> foundLeafTextures = Sets.newHashSet();
			for (TextureAtlasSprite icon : mapAtlas.values())
				for (ILeafTextureRecognizer recognizer : recognizers)
					if (recognizer.isLeafTexture(icon))
						foundLeafTextures.add(icon.getIconName());
			for (String resourceLocation : foundLeafTextures) {
				BetterFoliage.log.debug(String.format("Found non-block-registered leaf texture: %s", resourceLocation));
				blockTextures.registerIcon(new ResourceLocation(domainName, resourceLocation).toString());
			}
		}
	}
	
	@SubscribeEvent
	public void endTextureReload(TextureStitchEvent.Post event) {
		blockTextures = null;
		if (event.map.getTextureType() == 0) {
			BetterFoliage.log.info(String.format("Generated %d leaf textures", counter));
			
			// don't leave a mess
			Map<String, IResourceManager> domainManagers = ReflectionUtil.getDomainResourceManagers();
			if (domainManagers == null) return;
			domainManagers.remove(domainName);
		}
	}
	
}
