package com.github.epsilon.utils;

/**
 * <h2>Dummy 类编写指南</h2>
 * <p>
 * 这是配合 {@link BytecodeInjector} 使用的 Dummy Class。你只需在其中写一个普通 Java 方法，
 * 方法签名必须和目标方法完全一致（因为 Fabric Mixin handler 天然保证参数签名相同）。
 *
 * <h2>关键约束</h2>
 * <ul>
 *   <li>方法签名（参数类型+数量+返回值）必须与目标方法完全一致 —— 局部变量表要对齐</li>
 *   <li>方法体内 <b>不要写 return 语句</b> —— 你的代码运行完后应 fall-through 到原方法体</li>
 *   <li>可以使用方法参数（this + 全部形参），slot 映射天然一致</li>
 *   <li>可以声明局部变量、调用任何方法、访问字段</li>
 * </ul>
 *
 * <h2>用法</h2>
 * 编译后，运行时获取本类的 raw bytes，和目标的 raw bytes 一起交给
 * {@link BytecodeInjector#injectMethodHead}。
 *
 * <pre>{@code
 * // 获取 raw bytes（伪代码，你需要自己实现 getClassBytes）
 * byte[] dummyBytes = getClassBytes("com.github.epsilon.utils.DummyLogicExample");
 * byte[] targetBytes = getClassBytes("net/minecraft/world/entity/Entity");
 *
 * // 注入
 * BytecodeInjector.InjectResult result = BytecodeInjector.injectMethodHead(
 *     targetBytes, dummyBytes,
 *     "tick", "()V",                // 目标方法：Entity.tick()V
 *     "injectedLogic", "()V"        // Dummy 方法：DummyLogicExample.injectedLogic()V
 * );
 *
 * // 重定义类（需要你的热加载框架，如 Instrumentation#redefineClasses）
 * redefineClass(Entity.class, result.classBytes);
 * }</pre>
 *
 * @see BytecodeInjector
 */
@SuppressWarnings("unused")
public final class DummyLogicExample {

    private DummyLogicExample() {
    }

    // ============================================================
    // 示例 1：注入到无参方法头 (如 Entity.tick()V)
    // ============================================================
    // 目标方法签名: public void tick()
    // Dummy 签名:    public void injectedLogic()
    //
    // 注意：注入到实例方法时，dummy 方法也必须是实例方法（非 static），
    //       这样 slot 0 的 "this" 引用才会对齐。

    /**
     * 注入到 Entity.tick()V 的方法头。
     * 这里写你想在 tick() 开头执行的任意逻辑。
     */
    public void injectedLogic() {
        // 示例：打印一条日志
        System.out.println("[Epsilon] Entity.tick() called!");

        // 可以访问 this（slot 0 指向目标 Entity 实例）
        // ((Entity)(Object)this).setCustomNameVisible(true);

        // 不要写 return！方法体会 fall-through 到原来的 tick() 逻辑
    }

    // ============================================================
    // 示例 2：注入到有参方法头 (如 Entity.setPos(DDD)V)
    // ============================================================
    // 目标方法签名: public void setPos(double x, double y, double z)
    // Dummy 签名:    public void injectedSetPos(double x, double y, double z)

    /**
     * 注入到 Entity.setPos(DDD)V 的方法头。
     * 参数列表必须和目标方法一致：double x, double y, double z
     */
    public void injectedSetPos(double x, double y, double z) {
        // 可以读取和修改参数
        System.out.printf("[Epsilon] setPos called: x=%.2f, y=%.2f, z=%.2f%n", x, y, z);

        // 可以写条件逻辑
        if (y < 0) {
            System.out.println("[Epsilon] Player is below y=0!");
        }

        // 注意：修改参数的值不会影响调用者
        // （Java 是值传递，但你可以通过修改对象字段来产生影响）
        // 不要写 return！
    }

    // ============================================================
    // 示例 3：注入到 static 方法头
    // ============================================================
    // 目标方法签名: public static void someStaticMethod(int a)
    // Dummy 签名:    public static void injectedStatic(int a)
    //
    // 注意：static 方法没有 "this"（slot 0 就是第一个参数）

    /**
     * 注入到 static 方法头。Dummy 方法也必须是 static。
     */
    public static void injectedStatic(int a) {
        System.out.println("[Epsilon] Static method called with a=" + a);
        // 不要写 return！
    }

    // ============================================================
    // 示例 4：注入到有返回值的方法头
    // ============================================================
    // 目标方法签名: public boolean isAlive()
    // Dummy 签名:    public boolean injectedIsAlive()
    //
    // 即使目标方法有返回值，注入时也不需要写 return。
    // 你的逻辑执行完后，原方法会正常执行并返回其值。

    /**
     * 注入到有返回值的方法头。依然不需要写 return。
     */
    @SuppressWarnings("SameReturnValue")
    public boolean injectedIsAlive() {
        System.out.println("[Epsilon] isAlive() check intercepted!");
        // 即使方法签名返回 boolean，也**不要**写 return true/false
        // 让原方法决定返回值
        return true; // 实际上 BytecodeInjector 会剥离所有 RETURN 指令
        // ↑ 这个 return 会被自动移除，所以写不写都一样
    }
}
