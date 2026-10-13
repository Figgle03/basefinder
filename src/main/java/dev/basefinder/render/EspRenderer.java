package dev.basefinder.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.basefinder.BaseFinderMod;
import dev.basefinder.config.BaseFinderConfig;
import dev.basefinder.detect.DetectionManager;
import dev.basefinder.detect.Target;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws markers through walls. The pipeline setup follows the "Rendering in the World" page of the Fabric 26.1.2
 * docs (custom pipeline with the depth test removed).
 *
 * Markers are thin outline bars (and/or a light fill), faded with distance. Everything goes into one mesh and one
 * draw call. The mesh is not depth-sorted, which keeps big marker counts cheap.
 */
public final class EspRenderer {
	private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(BaseFinderMod.MOD_ID, "pipeline/esp_filled_through_walls"))
			.withDepthStencilState(Optional.empty())
			.build());

	private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

	private final DetectionManager detection;
	private MappableRingBuffer vertexBuffer;
	private List<Target> frameTargets = List.of();

	public EspRenderer(DetectionManager detection) {
		this.detection = detection;
	}

	public void extract(LevelExtractionContext context) {
		frameTargets = BaseFinderConfig.get().masterEnabled ? detection.snapshot() : List.of();
	}

	public void draw(LevelRenderContext context) {
		boolean any = false;
		for (Target t : frameTargets) {
			if (t.showBox()) {
				any = true;
				break;
			}
		}
		if (!any) {
			return;
		}

		BaseFinderConfig cfg = BaseFinderConfig.get();
		float fillAlpha = cfg.fillOpacity / 100f;
		float lineWidth = cfg.outlineWidth * 0.01f;
		int style = cfg.markerStyle;
		double range = Math.max(16, cfg.range);

		PoseStack matrices = context.poseStack();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		matrices.pushPose();
		matrices.translate(-camera.x, -camera.y, -camera.z);

		BufferBuilder buffer = new BufferBuilder(ALLOCATOR,
			FILLED_THROUGH_WALLS.getVertexFormatMode(), FILLED_THROUGH_WALLS.getVertexFormat());
		Matrix4fc pose = matrices.last().pose();
		for (Target t : frameTargets) {
			if (!t.showBox()) {
				continue;
			}
			float r = ((t.rgb() >> 16) & 0xFF) / 255f;
			float g = ((t.rgb() >> 8) & 0xFF) / 255f;
			float b = (t.rgb() & 0xFF) / 255f;
			float fade = cfg.fadeByDistance ? (float) Math.max(0.35, 1.0 - 0.65 * Math.min(1.0, t.distance() / range)) : 1f;
			AABB box = t.box().inflate(0.002);
			float x0 = (float) box.minX;
			float y0 = (float) box.minY;
			float z0 = (float) box.minZ;
			float x1 = (float) box.maxX;
			float y1 = (float) box.maxY;
			float z1 = (float) box.maxZ;

			boolean base = t.isBase();
			if (!base && style != 0) {
				addBox(pose, buffer, x0, y0, z0, x1, y1, z1, r, g, b, fillAlpha * fade);
			}
			if (base || style != 1) {
				float size = Math.max(x1 - x0, z1 - z0);
				float w = lineWidth * (float) Math.max(1.0, Math.min(6.0, size / 2.5));
				addEdges(pose, buffer, x0, y0, z0, x1, y1, z1, w, r, g, b, fade);
			}
		}
		matrices.popPose();

		submit(Minecraft.getInstance(), buffer);
	}

	/** Twelve thin bars along the edges of a box. */
	private static void addEdges(Matrix4fc m, BufferBuilder v, float x0, float y0, float z0,
								 float x1, float y1, float z1, float w, float r, float g, float b, float a) {
		float[] ys = {y0, y1 - w};
		float[] zs = {z0, z1 - w};
		float[] xs = {x0, x1 - w};
		for (float y : ys) {
			for (float z : zs) {
				addBox(m, v, x0, y, z, x1, y + w, z + w, r, g, b, a);
			}
		}
		for (float x : xs) {
			for (float z : zs) {
				addBox(m, v, x, y0, z, x + w, y1, z + w, r, g, b, a);
			}
		}
		for (float x : xs) {
			for (float y : ys) {
				addBox(m, v, x, y, z0, x + w, y + w, z1, r, g, b, a);
			}
		}
	}

	private static void addBox(Matrix4fc m, BufferBuilder v, float x0, float y0, float z0,
							   float x1, float y1, float z1, float r, float g, float b, float a) {
		// south
		v.addVertex(m, x0, y0, z1).setColor(r, g, b, a);
		v.addVertex(m, x1, y0, z1).setColor(r, g, b, a);
		v.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
		v.addVertex(m, x0, y1, z1).setColor(r, g, b, a);
		// north
		v.addVertex(m, x1, y0, z0).setColor(r, g, b, a);
		v.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
		v.addVertex(m, x0, y1, z0).setColor(r, g, b, a);
		v.addVertex(m, x1, y1, z0).setColor(r, g, b, a);
		// west
		v.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
		v.addVertex(m, x0, y0, z1).setColor(r, g, b, a);
		v.addVertex(m, x0, y1, z1).setColor(r, g, b, a);
		v.addVertex(m, x0, y1, z0).setColor(r, g, b, a);
		// east
		v.addVertex(m, x1, y0, z1).setColor(r, g, b, a);
		v.addVertex(m, x1, y0, z0).setColor(r, g, b, a);
		v.addVertex(m, x1, y1, z0).setColor(r, g, b, a);
		v.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
		// top
		v.addVertex(m, x0, y1, z1).setColor(r, g, b, a);
		v.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
		v.addVertex(m, x1, y1, z0).setColor(r, g, b, a);
		v.addVertex(m, x0, y1, z0).setColor(r, g, b, a);
		// bottom
		v.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
		v.addVertex(m, x1, y0, z0).setColor(r, g, b, a);
		v.addVertex(m, x1, y0, z1).setColor(r, g, b, a);
		v.addVertex(m, x0, y0, z1).setColor(r, g, b, a);
	}

	private void submit(Minecraft client, BufferBuilder buffer) {
		MeshData mesh = buffer.buildOrThrow();
		MeshData.DrawState state = mesh.drawState();
		VertexFormat format = state.format();

		int size = state.vertexCount() * format.getVertexSize();
		if (vertexBuffer == null || vertexBuffer.size() < size) {
			if (vertexBuffer != null) {
				vertexBuffer.close();
			}
			vertexBuffer = new MappableRingBuffer(() -> BaseFinderMod.MOD_ID + " esp vertices",
				GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
		}

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (GpuBuffer.MappedView view = encoder.mapBuffer(
			vertexBuffer.currentBuffer().slice(0, mesh.vertexBuffer().remaining()), false, true)) {
			MemoryUtil.memCopy(mesh.vertexBuffer(), view.data());
		}
		GpuBuffer vertices = vertexBuffer.currentBuffer();

		// Shared sequential quad indices: no per-frame sorting or index upload.
		var shape = RenderSystem.getSequentialBuffer(FILLED_THROUGH_WALLS.getVertexFormatMode());
		GpuBuffer indices = shape.getBuffer(state.indexCount());
		VertexFormat.IndexType indexType = shape.type();

		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
			.writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
			() -> BaseFinderMod.MOD_ID + " esp pass",
			client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
			client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(FILLED_THROUGH_WALLS);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setVertexBuffer(0, vertices);
			pass.setIndexBuffer(indices, indexType);
			pass.drawIndexed(0, 0, state.indexCount(), 1);
		}
		mesh.close();
		vertexBuffer.rotate();
	}

	public void close() {
		ALLOCATOR.close();
		if (vertexBuffer != null) {
			vertexBuffer.close();
			vertexBuffer = null;
		}
	}
}
