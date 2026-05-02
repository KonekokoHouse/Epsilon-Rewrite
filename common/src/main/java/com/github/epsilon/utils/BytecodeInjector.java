package com.github.epsilon.utils;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;


/**
 * 免写ASM的方法头字节码注入工具。
 *
 * <h2>设计思路</h2>
 * 不想手写 ASM 来生成字节码？只需将自定义逻辑写在一个 <b>Dummy Class</b> 的普通 Java 方法里，
 * 让它随项目一起编译。运行时，通过 {@link #injectMethodHead} 把这个 Dummy 方法的字节码
 * 提取出来，注入到目标方法的最开头。
 *
 * <h2>为什么可行</h2>
 * 因为 Dummy 方法的形参和 Target 方法完全一致（Fabric Mixin 天然保证），局部变量表
 * 的 slot 映射是 1:1 的，不需要任何重映射。
 *
 * <h2>用法示例</h2>
 * <pre>{@code
 * // 1. 编写你的逻辑（Dummy Class 与方法）
 * public class DummyLogic {
 *     // 形参必须和目标方法一模一样（Mixin handler 天然满足）
 *     public void dummyMethod(Entity entity, int x, int y, int z) {
 *         System.out.println("Before original method!");
 *         entity.setPos(x + 1, y, z);  // 你的任意逻辑
 *         // 注意：不要写 return！（方法头注入不应提前返回）
 *     }
 * }
 *
 * // 2. 运行时注入
 * byte[] targetBytes = getClassBytes("net/minecraft/world/entity/Entity");
 * byte[] dummyBytes = getClassBytes("com/example/DummyLogic");
 * byte[] modified = BytecodeInjector.injectMethodHead(
 *     targetBytes, dummyBytes,
 *     "methodName", "(Lnet/minecraft/world/entity/Entity;III)V",  // target
 *     "dummyMethod", "(Lnet/minecraft/world/entity/Entity;III)V"   // dummy
 * );
 * redefineClass("net.minecraft.world.entity.Entity", modified);
 * }</pre>
 *
 * @see <a href="https://asm.ow2.io/">ASM 9.x</a>
 * @see <a href="https://github.com/SpongePowered/Mixin">Sponge Mixin</a>
 */
public final class BytecodeInjector {

    private BytecodeInjector() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ---- 返回值类型 ----

    /**
     * 注入结果，包含修改后的字节码和诊断信息。
     */
    public static final class InjectResult {
        public final byte[] classBytes;
        public final int instructionsInjected;
        public final int maxStackBefore;
        public final int maxStackAfter;
        public final int maxLocalsBefore;
        public final int maxLocalsAfter;

        InjectResult(byte[] classBytes, int instructionsInjected,
                     int maxStackBefore, int maxStackAfter,
                     int maxLocalsBefore, int maxLocalsAfter) {
            this.classBytes = classBytes;
            this.instructionsInjected = instructionsInjected;
            this.maxStackBefore = maxStackBefore;
            this.maxStackAfter = maxStackAfter;
            this.maxLocalsBefore = maxLocalsBefore;
            this.maxLocalsAfter = maxLocalsAfter;
        }
    }

    // ---- 公开 API ----

    /**
     * 将 Dummy 类中指定方法的方法体注入到目标类同名同参方法的方法头（最开头）。
     *
     * @param targetClassBytes 目标类的 raw bytes
     * @param dummyClassBytes  Dummy 类的 raw bytes（包含要注入的逻辑）
     * @param targetMethodName 目标方法名
     * @param targetMethodDesc 目标方法描述符
     * @param dummyMethodName  Dummy 方法名（要提取的方法）
     * @param dummyMethodDesc  Dummy 方法描述符（必须与 target 一致）
     * @return 注入结果，包含修改后的字节码
     * @throws IllegalArgumentException 如果方法不存在或参数签名不匹配
     */
    public static InjectResult injectMethodHead(
            byte[] targetClassBytes,
            byte[] dummyClassBytes,
            String targetMethodName,
            String targetMethodDesc,
            String dummyMethodName,
            String dummyMethodDesc) {

        // 1. 解析两个类
        ClassNode targetNode = parseClass(targetClassBytes);
        ClassNode dummyNode = parseClass(dummyClassBytes);

        // 2. 找到各自的方法
        MethodNode targetMethod = findMethod(targetNode, targetMethodName, targetMethodDesc);
        MethodNode dummyMethod = findMethod(dummyNode, dummyMethodName, dummyMethodDesc);

        if (targetMethod == null) {
            throw new IllegalArgumentException(
                    "Target method not found: " + targetMethodName + targetMethodDesc
                            + " in " + targetNode.name);
        }
        if (dummyMethod == null) {
            throw new IllegalArgumentException(
                    "Dummy method not found: " + dummyMethodName + dummyMethodDesc
                            + " in " + dummyNode.name);
        }

        // 3. 验证参数签名兼容
        validateParamCompatibility(targetMethod, dummyMethod);

        // 4. 记录注入前的状态
        int maxStackBefore = targetMethod.maxStack;
        int maxLocalsBefore = targetMethod.maxLocals;

        // 5. 移除 dummy 方法末尾的所有 RETURN 族指令
        //    方法头注入不应包含 return，否则原方法体永远得不到执行
        stripReturnInstructions(dummyMethod);

        // 6. 如果有 try-catch 块，迁移到目标方法
        //    （标签会随 InsnList.insert() 一起自然迁移）
        if (dummyMethod.tryCatchBlocks != null && !dummyMethod.tryCatchBlocks.isEmpty()) {
            targetMethod.tryCatchBlocks.addAll(dummyMethod.tryCatchBlocks);
            dummyMethod.tryCatchBlocks.clear();
        }

        // 7. 核心操作：将 dummy 的全部指令插入到 target 方法头部
        //    InsnList.insert(InsnList) 会把整个链表插入到 firstInsn 之前
        //    并调用 removeAll(false) 清空源链表（指令被"移动"而非复制）
        //    标签 LabelNode 随指令一起迁移，因此不需要 cloneLabels
        int instructionsInjected = dummyMethod.instructions.size();
        targetMethod.instructions.insert(dummyMethod.instructions);

        // 8. 调整 maxStack 和 maxLocals（取较大值）
        targetMethod.maxStack = Math.max(targetMethod.maxStack, dummyMethod.maxStack);
        targetMethod.maxLocals = Math.max(targetMethod.maxLocals, dummyMethod.maxLocals);

        // 9. 写出字节码（COMPUTE_FRAMES 让 ASM 自动重算栈帧）
        byte[] result = writeClass(targetNode);

        return new InjectResult(
                result, instructionsInjected,
                maxStackBefore, targetMethod.maxStack,
                maxLocalsBefore, targetMethod.maxLocals
        );
    }

    // ---- 内部工具方法 ----

    /**
     * 用 ASM ClassReader 将 raw bytes 解析为 ClassNode 树。
     * 使用 EXPAND_FRAMES 确保栈帧信息完整展开。
     */
    private static ClassNode parseClass(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode(Opcodes.ASM9);
        reader.accept(node, ClassReader.EXPAND_FRAMES);
        return node;
    }

    /**
     * 在 ClassNode 中按 name + descriptor 查找方法。
     */
    private static MethodNode findMethod(ClassNode classNode, String name, String desc) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) {
                return method;
            }
        }
        return null;
    }

    /**
     * 验证目标方法和 Dummy 方法的参数兼容性。
     * 因为局部变量表要对齐，所以参数类型必须严格一致。
     */
    private static void validateParamCompatibility(MethodNode target, MethodNode dummy) {
        Type[] targetArgs = Type.getArgumentTypes(target.desc);
        Type[] dummyArgs = Type.getArgumentTypes(dummy.desc);

        if (targetArgs.length != dummyArgs.length) {
            throw new IllegalArgumentException(String.format(
                    "Parameter count mismatch: target has %d, dummy has %d. "
                            + "Methods must have identical parameter lists for LVT alignment.",
                    targetArgs.length, dummyArgs.length));
        }

        for (int i = 0; i < targetArgs.length; i++) {
            if (!targetArgs[i].equals(dummyArgs[i])) {
                throw new IllegalArgumentException(String.format(
                        "Parameter type mismatch at index %d: target=%s, dummy=%s. "
                                + "All parameter types must be identical for LVT alignment.",
                        i, targetArgs[i].getClassName(), dummyArgs[i].getClassName()));
            }
        }
    }

    /**
     * 从方法末尾剥离所有 RETURN 族指令（IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN）。
     *
     * <p>方法头注入的场景下，注入的代码不应该包含 return。否则会导致原方法体
     * 永远执行不到。此方法从后往前遍历，删除所有 return 族指令，直到遇到
     * 非 return 的指令为止。
     *
     * @return 被移除的指令数量
     */
    private static int stripReturnInstructions(MethodNode method) {
        int removed = 0;
        InsnList insns = method.instructions;
        AbstractInsnNode insn = insns.getLast();

        while (insn != null && isReturnOpcode(insn.getOpcode())) {
            AbstractInsnNode prev = insn.getPrevious();
            insns.remove(insn);
            removed++;
            insn = prev;
        }

        return removed;
    }

    /**
     * 判断给定 opcode 是否为 RETURN 族指令。
     * 覆盖范围：IRETURN(172) ~ RETURN(177)
     */
    private static boolean isReturnOpcode(int opcode) {
        return opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN;
    }

    /**
     * 用 ClassWriter 将 ClassNode 写回 byte[]。
     * 使用 COMPUTE_FRAMES 自动重算栈帧，这比 COMPUTE_MAXS 更准确
     * 但要求 classpath 上有所有引用到的类（Minecraft 环境下通常满足）。
     * 如果帧计算失败，回退到 COMPUTE_MAXS。
     */
    private static byte[] writeClass(ClassNode classNode) {
        try {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            classNode.accept(writer);
            return writer.toByteArray();
        } catch (Exception e) {
            // 帧计算失败（通常是 classpath 不完整），回退到仅计算 maxs
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(writer);
            return writer.toByteArray();
        }
    }
}
