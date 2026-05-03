package com.github.epsilon.utils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mixin → Target Minecraft 类的完整映射表。
 *
 * <h2>用法</h2>
 * <pre>{@code
 * for (var entry : MixinMappingTable.COMMON_MIXINS.entrySet()) {
 *     String mixinInternalName = entry.getKey();
 *     String targetInternalName = entry.getValue();
 *
 *     byte[] mixinBytes = getClassBytes(mixinInternalName);    // 你的 bytes 提供器
 *     byte[] targetBytes = getClassBytes(targetInternalName);
 *
 *     byte[] transformed = MixinRuntimeLauncher.applyMixin(
 *         targetInternalName.replace('/', '.'),
 *         targetBytes,
 *         mixinBytes
 *     );
 *     redefineClass(targetInternalName.replace('/', '.'), transformed);
 * }
 * }</pre>
 *
 * <h2>说明</h2>
 * <ul>
 *   <li>Key = Mixin 类 internal name（斜杠分隔）</li>
 *   <li>Value = Target Minecraft 类 internal name（斜杠分隔）</li>
 *   <li>顺序与 {@code epsilon.mixins.json} 注册顺序一致</li>
 *   <li>Accessor 接口（I*）只提供 getter，不含业务逻辑注入，可以不热重定义</li>
 *   <li>Fabric/NeoForge 的 MixinMinecraft 和 MixinGuiRenderer 是 loader 专用，热重定义时用 COMMON_MIXINS 即可</li>
 * </ul>
 *
 * @see MixinRuntimeLauncher
 * @see BytecodeInjector
 */
public final class MixinMappingTable {

    private MixinMappingTable() {
    }

    /**
     * Common 模块的 Mixin → Target 完整映射（43 条）。
     * 使用 LinkedHashMap 保持注册顺序。
     */
    public static final Map<String, String> COMMON_MIXINS = new LinkedHashMap<>();

    static {
        // === Accessor 接口（5 个）===
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/IAbstractContainerScreen",
            "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/IMinecraft",
            "net/minecraft/client/Minecraft"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/IReloadState",
            "net/minecraft/client/ResourceLoadStateTracker$ReloadState"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/IResourceLoadStateTracker",
            "net/minecraft/client/ResourceLoadStateTracker"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/IServerboundMovePlayerPacket",
            "net/minecraft/network/protocol/game/ServerboundMovePlayerPacket"
        );

        // === 渲染相关 Mixin（11 个）===
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinAvatarRenderer",
            "net/minecraft/client/renderer/entity/player/AvatarRenderer"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinBlockCollisions",
            "net/minecraft/world/level/BlockCollisions"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinCamera",
            "net/minecraft/client/Camera"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinChatComponent",
            "net/minecraft/client/gui/components/ChatComponent"  // targets inner: DrawingFocusedGraphicsAccess, DrawingBackgroundGraphicsAccess
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinEntityRenderer",
            "net/minecraft/client/renderer/entity/LivingEntityRenderer"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinGameRenderer",
            "net/minecraft/client/renderer/GameRenderer"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinHumanoidMobRenderer",
            "net/minecraft/client/renderer/entity/HumanoidMobRenderer"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinItemInHandRenderer",
            "net/minecraft/client/renderer/ItemInHandRenderer"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinLevelRenderer",
            "net/minecraft/client/renderer/LevelRenderer"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinLivingEntityRenderer",
            "net/minecraft/client/renderer/entity/LivingEntityRenderer"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinParticleManager",
            "net/minecraft/client/particle/ParticleEngine"
        );

        // === 客户端核心 Mixin（11 个）===
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinClientLevel",
            "net/minecraft/client/multiplayer/ClientLevel"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinClientPacketListener",
            "net/minecraft/client/multiplayer/ClientPacketListener"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinConnection",
            "net/minecraft/network/Connection"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinGlDebug",
            "com/mojang/blaze3d/opengl/GlDebug"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinGui",
            "net/minecraft/client/gui/Gui"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinGuiRenderer",
            "net/minecraft/client/gui/render/GuiRenderer"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinLightmap",
            "net/minecraft/client/renderer/Lightmap"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinMinecraft",
            "net/minecraft/client/Minecraft"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinMouseHandler",
            "net/minecraft/client/MouseHandler"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinScreenEffectRenderer",
            "net/minecraft/client/renderer/ScreenEffectRenderer"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinWindow",
            "com/mojang/blaze3d/platform/Window"
        );

        // === 实体/玩家 Mixin（9 个）===
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinEntity",
            "net/minecraft/world/entity/Entity"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinFireworkRocketEntity",
            "net/minecraft/world/entity/projectile/FireworkRocketEntity"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinKeyboardInput",
            "net/minecraft/client/player/KeyboardInput"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinLivingEntity",
            "net/minecraft/world/entity/LivingEntity"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinLocalPlayer",
            "net/minecraft/client/player/LocalPlayer"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinMultiPlayerGameMode",
            "net/minecraft/client/multiplayer/MultiPlayerGameMode"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinPlayer",
            "net/minecraft/world/entity/player/Player"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinProjection",
            "net/minecraft/client/renderer/Projection"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinMobEffectFogEnvironment",
            "net/minecraft/client/renderer/fog/environment/MobEffectFogEnvironment"
        );

        // === 输入/物品/方块等 Mixin（7 个）===
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinKeyboardHandler",
            "net/minecraft/client/KeyboardHandler"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinMain",
            "net/minecraft/client/main/Main"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinTitleScreen",
            "net/minecraft/client/gui/screens/TitleScreen"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinItem",
            "net/minecraft/world/item/Item"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinFlowingFluid",
            "net/minecraft/world/level/material/FlowingFluid"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinWebBlock",
            "net/minecraft/world/level/block/WebBlock"
        );
        COMMON_MIXINS.put(
            "com/github/epsilon/mixins/MixinDataComponentInitializers",
            "net/minecraft/core/component/DataComponentInitializers"
        );
    }

    /**
     * 仅含真正注入逻辑的 Mixin（排除 Accessor 接口，共 38 条）。
     * 热重定义时通常只需要这个。
     */
    public static final Map<String, String> INJECTION_MIXINS = new LinkedHashMap<>();

    static {
        for (var entry : COMMON_MIXINS.entrySet()) {
            String simpleName = entry.getKey().substring(entry.getKey().lastIndexOf('/') + 1);
            if (!simpleName.startsWith("I")) {
                INJECTION_MIXINS.put(entry.getKey(), entry.getValue());
            }
        }
    }

    // ==================== 便捷内部名获取 ====================

    /** 获取 Mixin 类的 internal name */
    public static String mixinInternalName(String simpleName) {
        return "com/github/epsilon/mixins/" + simpleName;
    }

    /** 获取 Target MC 类的 internal name（未包含在表中的返回 null） */
    public static String targetInternalName(String mixinSimpleName) {
        return COMMON_MIXINS.get(mixinInternalName(mixinSimpleName));
    }

}
