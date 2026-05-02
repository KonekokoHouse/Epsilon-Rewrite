package com.github.epsilon.utils;

import com.github.epsilon.events.Cancellable;

/**
 * 方法拦截事件 —— 配合 {@link BytecodeInjector} 实现形参转发+回写。
 *
 * <h2>设计思路</h2>
 * 运行时，Dummy 方法的字节码被注入到目标方法头。因为两个方法的参数签名一致，
 * 它们在 JVM 局部变量表中共享完全相同的 slot。这意味着：<b>在 Dummy 方法里对形参的
 * 重新赋值会直接修改原方法看到的参数值</b>。
 *
 * <h2>字节码原理</h2>
 * 假设目标方法和 Dummy 方法签名都是 {@code (IDLjava/lang/String;)V}：
 * <pre>
 * JVM 局部变量表（共享）：
 *   slot 0   →  this
 *   slot 1   →  int x
 *   slot 2-3 →  double y
 *   slot 4   →  String z
 *
 * Dummy 方法体被注入时执行：
 *   x = newValue;   →  ISTORE 1    (写入 slot 1)
 *
 * 原方法体执行时：
 *   ILOAD 1          → 读到的是修改后的值！
 * </pre>
 *
 * <h2>用法示例</h2>
 * <pre>{@code
 * // Dummy 类中
 * public void dummyMethod(int x, double y, String z) {
 *     MethodInterceptEvent event = new MethodInterceptEvent(x, y, z);
 *     EventBus.INSTANCE.post(event);
 *
 *     // ★ 关键：必须把修改后的值写回形参！
 *     // 因为编译器生成的 ISTORE/DSTORE/ASTORE 操作的正是
 *     // 原方法的局部变量槽位
 *     x = event.getInt(0);
 *     y = event.getDouble(1);
 *     z = (String) event.getObject(2);
 * }
 *
 * // 监听器中
 * EventBus.INSTANCE.register(MethodInterceptEvent.class, event -> {
 *     event.setInt(0, 42);   // 修改 int 参数
 *     event.cancel();        // 可选：取消原方法执行
 * });
 * }</pre>
 *
 * @see BytecodeInjector
 */
public class MethodInterceptEvent extends Cancellable {

    private final Object[] args;

    /**
     * @param args 方法的所有参数（按声明顺序）
     */
    public MethodInterceptEvent(Object... args) {
        this.args = args;
    }

    // ---- 类型安全的读写 ----

    public int getInt(int index) {
        return (int) args[index];
    }

    public void setInt(int index, int value) {
        args[index] = value;
    }

    public long getLong(int index) {
        return (long) args[index];
    }

    public void setLong(int index, long value) {
        args[index] = value;
    }

    public float getFloat(int index) {
        return (float) args[index];
    }

    public void setFloat(int index, float value) {
        args[index] = value;
    }

    public double getDouble(int index) {
        return (double) args[index];
    }

    public void setDouble(int index, double value) {
        args[index] = value;
    }

    public boolean getBoolean(int index) {
        return (boolean) args[index];
    }

    public void setBoolean(int index, boolean value) {
        args[index] = value;
    }

    @SuppressWarnings("unchecked")
    public <T> T getObject(int index) {
        return (T) args[index];
    }

    public void setObject(int index, Object value) {
        args[index] = value;
    }

    /** 参数个数 */
    public int getArgCount() {
        return args.length;
    }

    /** 直接访问内部数组（谨慎使用） */
    public Object[] getArgs() {
        return args;
    }
}
