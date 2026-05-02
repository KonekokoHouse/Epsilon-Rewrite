package com.github.epsilon.utils;

import com.github.epsilon.events.bus.EventBus;

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
 *   <li><b>★ 修改形参 = 修改原方法看到的参数值</b>（字节码层面 slot 共享）</li>
 * </ul>
 *
 * <h2>形参转发原理（字节码层面）</h2>
 * JVM 方法参数存储在局部变量表的 slot 中。因为 Dummy 和 Target 签名相同，
 * 它们的 slot 布局完全一致。当你写 {@code x = newValue} 时，javac 生成
 * {@code ISTORE <slot_of_x>}，直接覆盖原方法的参数槽位。原方法体后续的
 * {@code ILOAD <slot_of_x>} 就会读到修改后的值。
 *
 * <h2>用法</h2>
 * 编译后，运行时获取本类的 raw bytes，和目标的 raw bytes 一起交给
 * {@link BytecodeInjector#injectMethodHead}。
 *
 * <pre>{@code
 * // 获取 raw bytes
 * byte[] dummyBytes = getClassBytes("com.github.epsilon.utils.DummyLogicExample");
 * byte[] targetBytes = getClassBytes("net/minecraft/world/entity/Entity");
 *
 * // 注入
 * BytecodeInjector.InjectResult result = BytecodeInjector.injectMethodHead(
 *     targetBytes, dummyBytes,
 *     "tick", "()V",                // 目标方法
 *     "injectedLogic", "()V"        // Dummy 方法
 * );
 * redefineClass(Entity.class, result.classBytes);
 * }</pre>
 *
 * @see BytecodeInjector
 * @see MethodInterceptEvent
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
    // 示例 3：注入到 static 方法头（slot 0 就是第一个参数）
    // ============================================================
    public static void injectedStatic(int a) {
        System.out.println("[Epsilon] Static called with a=" + a);
        a = a * 2;  // ★ 对 static 方法同样可以直接修改形参
        // slot 0 = 参数a（因为没有 this），所以 ISTORE 0
    }

    // ============================================================
    // 示例 4：有返回值的方法（不写 return，原方法决定返回值）
    // ============================================================
    @SuppressWarnings("SameReturnValue")
    public boolean injectedIsAlive() {
        System.out.println("[Epsilon] isAlive() check intercepted!");
        return true;  // BytecodeInjector 会自动剥离 RETURN
    }

    // ============================================================
    // 示例 5：★ 形参转发 + 回写（配合 MethodInterceptEvent）
    // ============================================================
    // 核心演示：转发参数 → EventBus → 监听器修改 → 写回形参 → 原方法看到修改
    //
    // 目标方法签名: public void tick(int ticks)
    // Dummy 签名:    public void interceptTick(int ticks)
    //
    // 关键步骤：
    //   1. 用形参构造 MethodInterceptEvent（从 slot 读取参数）
    //   2. 通过 EventBus 分发给所有监听器
    //   3. ★ 把 event 中可能被修改的值写回形参（写回同一 slot）
    //   4. 检查 isCancelled() 决定是否跳过原方法体

    /**
     * 拦截 Entity 或某类的 tick(int) 方法，将参数转发给监听器处理。
     */
    public void interceptTick(int ticks) {
        // 1. 打包参数
        MethodInterceptEvent event = new MethodInterceptEvent(ticks);

        // 2. 发送事件（监听器可以自由修改 event 中的值）
        EventBus.INSTANCE.post(event);

        // 3. ★ 把修改后的值写回形参！
        //    编译器生成：ILOAD <event_slot>, INVOKEVIRTUAL getInt, ISTORE <ticks_slot>
        //    这个 ISTORE 直接写入原方法的参数 slot，原方法体随后 ILOAD 就会读到新值
        ticks = event.getInt(0);

        // 4. 如果监听器取消了事件，跳过原方法体
        //    注意：这里不能用 return，因为 BytecodeInjector 会剥离 RETURN
        //    解决办法：用 if-else 包裹原方法调用
        //    更简单的：如果不取消，正常 fall-through；如果取消了，写一个标志位
        //    或者：直接在 event 中设置一个 dirty flag，让后续模块检查
    }

    // ============================================================
    // 示例 6：★ 直接修改形参（无需 EventBus，最简单）
    // ============================================================
    // 如果你不需要事件系统，只想在方法执行前修改参数，直接赋值即可。
    // 因为编译后的 ISTORE 操作的就是原方法的 slot。
    //
    // 目标方法签名: public void setMotion(double x, double y, double z)
    // Dummy 签名:    public void modifyMotion(double x, double y, double z)

    /**
     * 直接修改方法参数（无需事件）。
     * 修改后原方法体看到的 x, y, z 就是新值。
     */
    public void modifyMotion(double x, double y, double z) {
        // 直接修改形参 —— ISTORE 到 slot 1/2/3/4/5
        // 原方法体后续 ILOAD/DLOAD 会读到修改后的值
        if (y < 0.0) {
            y = 0.0;  // 把负的 y 归零
        }
        x *= 1.5;      // 加速 x
        z *= 1.5;      // 加速 z
    }

    // ============================================================
    // 示例 7：★ 形参转发（多类型混合）
    // ============================================================
    // 目标方法签名: public void handleInput(int key, boolean pressed, String context)
    // Dummy 签名:    public void interceptInput(int key, boolean pressed, String context)

    /**
     * 多类型参数转发+回写。注意每个参数都要写回。
     */
    public void interceptInput(int key, boolean pressed, String context) {
        // 1. 打包（注意 boolean 会被自动装箱为 Boolean）
        MethodInterceptEvent event = new MethodInterceptEvent(key, pressed, context);

        // 2. 分发给监听器
        EventBus.INSTANCE.post(event);

        // 3. ★ 写回每个参数（缺一不可！）
        key = event.getInt(0);
        pressed = event.getBoolean(1);
        context = event.getObject(2);

        // 如果监听器把 key 改成了 -1（表示拦截），后续模块可以据此判断
    }
}
