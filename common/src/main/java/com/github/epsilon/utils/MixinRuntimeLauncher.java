package com.github.epsilon.utils;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

/**
 * 运行时启动 SpongePowered Mixin 变换管线的工具。
 *
 * <h2>解决的问题</h2>
 * 正常 Mixin 通过 classpath 加载目标类和 Mixin 类。本工具让你可以：
 * <ul>
 *   <li>用自定义 {@code byte[]} 提供<b>目标类的 raw bytes</b></li>
 *   <li>用自定义 {@code byte[]} 提供<b>Mixin 模板类的 raw bytes</b></li>
 *   <li>触发 Mixin 完整管线：描述符重映射 + {@code @Inject} + {@code @Overwrite} + 字段合并等</li>
 * </ul>
 *
 * <h2>Mixin 变换管线概述（源码追踪）</h2>
 * <pre>
 * IMixinTransformer.transformClass(env, name, byte[])
 *   → MixinTransformer.transformClass()
 *     → MixinProcessor.applyMixins(env, name, classNode)
 *       → 遍历 MixinConfig → hasMixinsFor(name) → getMixinsFor(name)
 *       → TargetClassContext(env, extensions, sessionId, name, classNode, mixins)
 *       → TargetClassContext.applyMixins()
 *         → MixinApplicatorStandard.apply(mixins)
 *           → 对每个 MixinInfo: mixin.createContextFor(target)
 *             → MixinPreProcessorStandard.createContextFor()
 *               → MixinTargetContext(mixin, classNode, target)
 *               → transformMethod() [描述符重映射]
 *               → prepareInjections() → applyInjections() [@Inject]
 *           → applyFields() / applyMethods() [合并到目标类]
 * </pre>
 *
 * <h2>实现原理</h2>
 * <p>
 * Mixin 管线的核心瓶颈在于：<b>Mixin 类必须通过 MixinConfig 注册，且必须能从 classpath 加载</b>。
 * 本工具通过反射直接操作 Mixin 内部数据结构（{@code MixinConfig.mixinMapping}、
 * {@code MixinInfo.state}），将自定义 ClassNode 注入到管线中，绕过 classpath 加载限制。
 * </p>
 *
 * <h2>提供两个入口点</h2>
 * <table>
 *   <tr><th>入口</th><th>正常 Mixin</th><th>本工具</th></tr>
 *   <tr>
 *     <td>目标类 bytes</td>
 *     <td>{@code transformClassBytes(name, _, basicClass)} — classLoader 提供</td>
 *     <td>{@code transformClass(env, name, classNode)} — 直接接受 ClassNode</td>
 *   </tr>
 *   <tr>
 *     <td>Mixin 类 bytes</td>
 *     <td>{@code MixinInfo.loadMixinClass()} → {@code getBytecodeProvider().getClassNode()} — classpath 加载</td>
 *     <td>反射替换 {@code MixinInfo.state} 中的 ClassNode → 自定义 bytes 解析的 ClassNode</td>
 *   </tr>
 * </table>
 *
 * <h2>前置条件</h2>
 * <ol>
 *   <li>Mixin 框架必须已初始化（在 Fabric/NeoForge 环境中默认满足）</li>
 *   <li>项目中至少要有一个在 {@code epsilon.mixins.json} 中注册的正常 Mixin 类（本工具复用其 MixinConfig）</li>
 *   <li>提供的 Mixin 类 raw bytes 必须包含合法的 {@code @Mixin} 注解</li>
 * </ol>
 *
 * @see BytecodeInjector 更轻量的字节码注入（不需要 @Mixin 注解，直接拼方法头）
 * @see <a href="https://github.com/SpongePowered/Mixin">SpongePowered Mixin 源码</a>
 */
public final class MixinRuntimeLauncher {

    private MixinRuntimeLauncher() {
    }

    /**
     * 一步完成：用自定义 bytes 启动 Mixin 变换管线。
     *
     * @param targetClassName 目标类全限定名，如 {@code "net.minecraft.client.Minecraft"}
     * @param targetRawBytes  目标类的 raw byte[]
     * @param mixinRawBytes   Mixin 模板类的 raw byte[]（必须有 {@code @Mixin} 注解）
     * @return 变换后的目标类 byte[]，如果目标不是任何 Mixin 的目标则返回原始 bytes
     */
    public static byte[] applyMixin(String targetClassName, byte[] targetRawBytes, byte[] mixinRawBytes) {
        try {
            // 1. 获取 Mixin 基础设施
            MixinEnvironment env = MixinEnvironment.getCurrentEnvironment();
            Object activeTransformer = env.getActiveTransformer();
            if (!(activeTransformer instanceof IMixinTransformer transformer)) {
                throw new IllegalStateException(
                    "No active IMixinTransformer. Mixin may not be initialized yet."
                );
            }

            // 2. 将两个 raw bytes 都解析为 ClassNode
            ClassNode mixinNode = toClassNode(mixinRawBytes);
            ClassNode targetNode = toClassNode(targetRawBytes);
            String targetInternalName = targetClassName.replace('.', '/');

            // 3. 核心：把自定义 Mixin ClassNode 注入到 Mixin 内部管线
            registerMixinWithCustomClassNode(transformer, targetClassName, mixinNode);

            // 4. 触发 Mixin 变换（修改 targetNode 本身，返回 true 表示有变换发生）
            boolean applied = transformer.transformClass(env, targetInternalName, targetNode);

            if (applied) {
                return toByteArray(targetNode);
            }
            // 未匹配到任何 Mixin，返回原始 bytes
            return targetRawBytes;
        } catch (Exception e) {
            throw new RuntimeException(
                "MixinRuntimeLauncher: Failed to apply mixin to " + targetClassName, e
            );
        }
    }

    /**
     * 注册一个自定义 Mixin 类到 Mixin 管线中。
     * <p>
     * 核心步骤（通过反射操作 Mixin 内部状态）：
     * <ol>
     *   <li>获取 {@code MixinTransformer → MixinProcessor → configs} 列表</li>
     *   <li>取第一个可用 MixinConfig 作为"宿主"</li>
     *   <li>取该 Config 中任意一个已有 MixinInfo 作为"模板"（继承其 parent/config/service 引用）</li>
     *   <li>创建新的 MixinInfo.State（包装我们的自定义 ClassNode）</li>
     *   <li>将 (targetClassName → 新 MixinInfo) 注入到 Config 的 mapping 中</li>
     * </ol>
     * </p>
     */
    private static void registerMixinWithCustomClassNode(
        IMixinTransformer transformer,
        String targetClassName,
        ClassNode mixinClassNode
    ) throws Exception {

        // ---------- 第 1 步：通过反射获取 MixinProcessor ----------
        // MixinTransformer 是 package-private final class，IMixinTransformer 是其公共接口
        Field processorField = findField(transformer.getClass(), "processor");
        Object mixinProcessor = processorField.get(transformer);

        // ---------- 第 2 步：获取 configs 列表 ----------
        @SuppressWarnings("unchecked")
        List<Object> configs = (List<Object>) findField(mixinProcessor.getClass(), "configs").get(mixinProcessor);

        if (configs.isEmpty()) {
            throw new IllegalStateException("No MixinConfig registered. At least one mixin must exist in epsilon.mixins.json.");
        }

        // 取第一个 Config 作为宿主（任意 Config 都可以，它们共享同一个 Extensions/SessionId）
        Object hostConfig = configs.get(0);

        // ---------- 第 3 步：获取 Config 的 mixinMapping ----------
        @SuppressWarnings("unchecked")
        Map<String, List<Object>> mixinMapping = (Map<String, List<Object>>)
            findField(hostConfig.getClass(), "mixinMapping").get(hostConfig);

        // 如果目标类已经注册过了，跳过
        if (mixinMapping.containsKey(targetClassName)) {
            return;
        }

        // ---------- 第 4 步：从 hostConfig 获取已有的 MixinInfo 作为模板 ----------
        // mixinMapping 中任意一个已有条目都可以提供 parent/service/extensions 引用
        Object templateMixinInfo = null;
        for (List<Object> mixins : mixinMapping.values()) {
            if (!mixins.isEmpty()) {
                templateMixinInfo = mixins.get(0);
                break;
            }
        }

        if (templateMixinInfo == null) {
            throw new IllegalStateException("No existing MixinInfo found to use as template.");
        }

        // ---------- 第 5 步：创建新的 MixinInfo（克隆模板的关键引用）----------
        Object newMixinInfo = createMixinInfoClone(templateMixinInfo, mixinClassNode, targetClassName, hostConfig);

        // ---------- 第 6 步：注入到 mapping 中 ----------
        mixinMapping.computeIfAbsent(targetClassName, k -> new ArrayList<>()).add(newMixinInfo);

        // 同时注入到 unhandledTargets（可选，用于审计）
        try {
            @SuppressWarnings("unchecked")
            Set<String> unhandledTargets = (Set<String>)
                findField(hostConfig.getClass(), "unhandledTargets").get(hostConfig);
            unhandledTargets.add(targetClassName);
        } catch (Exception ignored) {
            // unhandledTargets 仅用于审计，不影响功能
        }
    }

    /**
     * 克隆一个 MixinInfo 并用自定义 ClassNode 替换其 state。
     * <p>
     * MixinInfo 是 package-private 的，无法直接 new。我们利用
     * {@code Unsafe.allocateInstance()} 或反射构造器创建实例，然后注入字段。
     * </p>
     * <p>
     * 关键点：
     * <ul>
     *   <li>{@code MixinInfo.state} 包装了 ClassNode，在 {@code createContextFor()} 时克隆使用</li>
     *   <li>必须设置 {@code parent}, {@code className}, {@code name}, {@code priority} 等字段</li>
     *   <li>{@code MixinInfo.State} 是非静态内部类，需要 outer MixinInfo 引用</li>
     * </ul>
     * </p>
     */
    private static Object createMixinInfoClone(
        Object template,
        ClassNode customClassNode,
        String targetClassName,
        Object hostConfig
    ) throws Exception {

        Class<?> mixinInfoClass = template.getClass();

        // 使用 sun.misc.Unsafe 创建未初始化的实例（绕过构造器中的 loadMixinClass 调用）
        Object newInfo = allocateInstance(mixinInfoClass);

        // 复制模板的所有字段
        for (Field field : mixinInfoClass.getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object value = field.get(template);
                field.set(newInfo, value);
            } catch (IllegalAccessException ignored) {
            }
        }

        // 覆盖关键字段
        setField(mixinInfoClass, newInfo, "parent", hostConfig);
        setField(mixinInfoClass, newInfo, "name", customClassNode.name.substring(
            customClassNode.name.lastIndexOf('/') + 1));
        setField(mixinInfoClass, newInfo, "className", customClassNode.name.replace('/', '.'));

        // 创建新的 MixinInfo.State（内嵌我们的 ClassNode）
        // State 是非静态内部类: State(MixinInfo outer, ClassNode classNode, ClassInfo classInfo)
        Class<?> stateClass = findInnerClass(mixinInfoClass, "State");
        Constructor<?> stateCtor = stateClass.getDeclaredConstructor(
            mixinInfoClass, ClassNode.class, Class.forName("org.spongepowered.asm.mixin.transformer.ClassInfo")
        );
        stateCtor.setAccessible(true);
        Object newState = stateCtor.newInstance(newInfo, customClassNode, null);

        // 覆盖 state 字段（注意：可能叫 state 或 pendingState）
        setField(mixinInfoClass, newInfo, "state", newState);
        try {
            setField(mixinInfoClass, newInfo, "pendingState", null);
        } catch (Exception ignored) {
        }

        // 设置 declaredTargets（从 @Mixin 注解解析出来的目标类列表）
        // 这些目标在 prepareMixins() 阶段已经解析好了，我们只需确保它们包含我们的 target
        try {
            @SuppressWarnings("unchecked")
            List<Object> oldTargetClasses = (List<Object>)
                findField(mixinInfoClass, "targetClasses").get(newInfo);
            List<String> oldTargetClassNames = new ArrayList<>();
            for (Object t : oldTargetClasses) {
                oldTargetClassNames.add(t.toString());
            }
            @SuppressWarnings("unchecked")
            List<String> targetClassNames = (List<String>)
                findField(mixinInfoClass, "targetClassNames").get(newInfo);
            if (!targetClassNames.contains(targetClassName)) {
                targetClassNames.add(targetClassName);
            }
        } catch (Exception ignored) {
        }

        return newInfo;
    }

    // ==================== 辅助方法 ====================

    private static ClassNode toClassNode(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        return node;
    }

    private static byte[] toByteArray(ClassNode classNode) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " not found in " + clazz.getName() + " hierarchy");
    }

    private static void setField(Class<?> clazz, Object instance, String name, Object value) throws Exception {
        Field field = findField(clazz, name);
        field.set(instance, value);
    }

    private static Class<?> findInnerClass(Class<?> outerClass, String simpleName) throws ClassNotFoundException {
        for (Class<?> inner : outerClass.getDeclaredClasses()) {
            if (inner.getSimpleName().equals(simpleName)) {
                return inner;
            }
        }
        // 也可能定义在父类中
        if (outerClass.getSuperclass() != null) {
            return findInnerClass(outerClass.getSuperclass(), simpleName);
        }
        throw new ClassNotFoundException(simpleName + " not found in " + outerClass.getName());
    }

    private static Object allocateInstance(Class<?> clazz) throws Exception {
        try {
            // Java 9+ 首选
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            return unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, clazz);
        } catch (Exception e) {
            // 备选：反射调用默认构造器
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        }
    }
}
