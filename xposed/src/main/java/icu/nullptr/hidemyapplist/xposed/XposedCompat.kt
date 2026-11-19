package icu.nullptr.hidemyapplist.xposed

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.ClassLoader
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object XposedCompat {
    private const val TAG = "HMA-XposedCompat"

    @Volatile
    private var _module: XposedModule? = null

    val module: XposedModule
        get() = _module ?: error("XposedCompat has not been initialized with XposedModule")

    val isInitialized: Boolean
        get() = _module != null

    @Volatile
    var classLoader: ClassLoader? = null

    fun init(mod: XposedModule, cl: ClassLoader? = null) {
        _module = mod
        if (cl != null) {
            classLoader = cl
        }
    }

    class HookParam(
        val chain: XposedInterface.Chain,
        val method: Method,
        val thisObject: Any?,
        val args: Array<Any?>
    ) {
        var isResultSubstituted = false
            private set

        var result: Any? = null
            set(value) {
                field = value
                isResultSubstituted = true
            }

        var throwable: Throwable? = null
    }

    class Unhook(val handle: XposedInterface.HookHandle, val hookedMethod: Method) {
        fun unhook() {
            try {
                handle.unhook()
            } catch (_: Throwable) {}
        }
    }

    fun findClass(className: String, cl: ClassLoader? = null): Class<*> {
        val loader = cl ?: classLoader ?: Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
        return Class.forName(className, false, loader)
    }

    fun findMethod(clazz: Class<*>, findSuper: Boolean = false, predicate: Method.() -> Boolean): Method {
        var current: Class<*>? = clazz
        while (current != null) {
            for (m in current.declaredMethods) {
                if (predicate(m)) {
                    m.isAccessible = true
                    return m
                }
            }
            if (!findSuper) break
            current = current.superclass
        }
        throw NoSuchMethodException("Method matching predicate not found in ${clazz.name}")
    }

    fun findMethod(className: String, findSuper: Boolean = false, classLoader: ClassLoader? = null, predicate: Method.() -> Boolean): Method {
        val clazz = findClass(className, classLoader)
        return findMethod(clazz, findSuper, predicate)
    }

    fun findMethodOrNull(clazz: Class<*>, findSuper: Boolean = false, predicate: Method.() -> Boolean): Method? {
        return runCatching { findMethod(clazz, findSuper, predicate) }.getOrNull()
    }

    fun findMethodOrNull(className: String, findSuper: Boolean = false, classLoader: ClassLoader? = null, predicate: Method.() -> Boolean): Method? {
        return runCatching { findMethod(className, findSuper, classLoader, predicate) }.getOrNull()
    }

    fun findConstructor(clazz: Class<*>, predicate: Constructor<*>.() -> Boolean): Constructor<*> {
        for (c in clazz.declaredConstructors) {
            if (predicate(c)) {
                c.isAccessible = true
                return c
            }
        }
        throw NoSuchMethodException("Constructor matching predicate not found in ${clazz.name}")
    }

    fun findConstructor(className: String, classLoader: ClassLoader? = null, predicate: Constructor<*>.() -> Boolean): Constructor<*> {
        val clazz = findClass(className, classLoader)
        return findConstructor(clazz, predicate)
    }

    fun findConstructorOrNull(clazz: Class<*>, predicate: Constructor<*>.() -> Boolean): Constructor<*>? {
        return runCatching { findConstructor(clazz, predicate) }.getOrNull()
    }

    fun findConstructorOrNull(className: String, classLoader: ClassLoader? = null, predicate: Constructor<*>.() -> Boolean): Constructor<*>? {
        return runCatching { findConstructor(className, classLoader, predicate) }.getOrNull()
    }

    fun findField(clazz: Class<*>, findSuper: Boolean = false, predicate: Field.() -> Boolean): Field {
        var current: Class<*>? = clazz
        while (current != null) {
            for (f in current.declaredFields) {
                if (predicate(f)) {
                    f.isAccessible = true
                    return f
                }
            }
            if (!findSuper) break
            current = current.superclass
        }
        throw NoSuchFieldException("Field matching predicate not found in ${clazz.name}")
    }

    fun findField(className: String, findSuper: Boolean = false, classLoader: ClassLoader? = null, predicate: Field.() -> Boolean): Field {
        val clazz = findClass(className, classLoader)
        return findField(clazz, findSuper, predicate)
    }

    fun findFieldOrNull(clazz: Class<*>, findSuper: Boolean = false, predicate: Field.() -> Boolean): Field? {
        return runCatching { findField(clazz, findSuper, predicate) }.getOrNull()
    }

    fun findFieldOrNull(className: String, findSuper: Boolean = false, classLoader: ClassLoader? = null, predicate: Field.() -> Boolean): Field? {
        return runCatching { findField(className, findSuper, classLoader, predicate) }.getOrNull()
    }
}

// Extensions for Member / Constructor
val Constructor<*>.paramCount: Int
    get() = parameterTypes.size

val Member.isStatic: Boolean
    get() = Modifier.isStatic(modifiers)

fun <K, V> MutableMap<K, V>.removeIf(predicate: (K, V) -> Boolean) {
    val it = entries.iterator()
    while (it.hasNext()) {
        val entry = it.next()
        if (predicate(entry.key, entry.value)) {
            it.remove()
        }
    }
}

fun Method.hookBefore(crossinline callback: (XposedCompat.HookParam) -> Unit): XposedCompat.Unhook {
    isAccessible = true
    try {
        XposedCompat.module.deoptimize(this)
    } catch (_: Throwable) {}

    val handle = XposedCompat.module.hook(this)
        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
        .intercept { chain ->
            val origArgs = chain.args
            val argsCopy = origArgs.toTypedArray()
            val param = XposedCompat.HookParam(chain, this, chain.thisObject, argsCopy)

            callback(param)

            if (param.isResultSubstituted) {
                return@intercept param.result
            }

            var modified = false
            for (i in origArgs.indices) {
                if (argsCopy[i] !== origArgs[i]) {
                    modified = true
                    break
                }
            }
            if (modified) {
                chain.proceed(argsCopy)
            } else {
                chain.proceed()
            }
        }
    return XposedCompat.Unhook(handle, this)
}

fun Method.hookAfter(crossinline callback: (XposedCompat.HookParam) -> Unit): XposedCompat.Unhook {
    isAccessible = true
    try {
        XposedCompat.module.deoptimize(this)
    } catch (_: Throwable) {}

    val handle = XposedCompat.module.hook(this)
        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
        .intercept { chain ->
            val origArgs = chain.args
            val argsCopy = origArgs.toTypedArray()
            val param = XposedCompat.HookParam(chain, this, chain.thisObject, argsCopy)

            var caughtThrowable: Throwable? = null
            try {
                param.result = chain.proceed()
            } catch (t: Throwable) {
                caughtThrowable = t
                param.throwable = t
            }

            callback(param)

            if (param.throwable != null && param.throwable !== caughtThrowable) {
                throw param.throwable!!
            }
            if (caughtThrowable != null && param.throwable != null) {
                throw caughtThrowable
            }
            return@intercept param.result
        }
    return XposedCompat.Unhook(handle, this)
}
