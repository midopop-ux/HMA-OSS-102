package icu.nullptr.hidemyapplist.xposed

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

object XposedHelpers {
    private val fieldCache = ConcurrentHashMap<Pair<Class<*>, String>, Field>()
    private val methodCache = ConcurrentHashMap<Triple<Class<*>, String, Int>, Method>()
    private val staticMethodCache = ConcurrentHashMap<Triple<Class<*>, String, Int>, Method>()

    fun findClass(className: String, classLoader: ClassLoader? = null): Class<*> =
        XposedCompat.findClass(className, classLoader)

    fun findField(clazz: Class<*>, fieldName: String): Field {
        val key = clazz to fieldName
        fieldCache[key]?.let { return it }

        var current: Class<*>? = clazz
        while (current != null) {
            for (f in current.declaredFields) {
                if (f.name == fieldName) {
                    f.isAccessible = true
                    fieldCache.putIfAbsent(key, f)
                    return f
                }
            }
            current = current.superclass
        }
        throw NoSuchFieldException("Field $fieldName not found in ${clazz.name}")
    }

    fun getObjectField(obj: Any?, fieldName: String): Any? =
        if (obj == null) null else findField(obj.javaClass, fieldName).get(obj)

    fun getIntField(obj: Any?, fieldName: String): Int =
        if (obj == null) 0 else findField(obj.javaClass, fieldName).getInt(obj)

    fun getBooleanField(obj: Any?, fieldName: String): Boolean =
        if (obj == null) false else findField(obj.javaClass, fieldName).getBoolean(obj)

    fun setBooleanField(obj: Any?, fieldName: String, value: Boolean) {
        if (obj != null) findField(obj.javaClass, fieldName).setBoolean(obj, value)
    }

    fun getStaticIntField(clazz: Class<*>, fieldName: String): Int {
        val f = findField(clazz, fieldName)
        if (!Modifier.isStatic(f.modifiers)) {
            throw IllegalArgumentException("Field $fieldName is not static in ${clazz.name}")
        }
        return f.getInt(null)
    }

    fun callMethod(obj: Any?, methodName: String, vararg args: Any?): Any? {
        if (obj == null) return null
        val clazz = obj.javaClass
        val key = Triple(clazz, methodName, args.size)
        methodCache[key]?.let { return it.invoke(obj, *args) }

        var current: Class<*>? = clazz
        while (current != null) {
            for (m in current.declaredMethods) {
                if (m.name == methodName && m.parameterTypes.size == args.size) {
                    m.isAccessible = true
                    methodCache.putIfAbsent(key, m)
                    return m.invoke(obj, *args)
                }
            }
            current = current.superclass
        }
        for (m in clazz.methods) {
            if (m.name == methodName && m.parameterTypes.size == args.size) {
                m.isAccessible = true
                methodCache.putIfAbsent(key, m)
                return m.invoke(obj, *args)
            }
        }
        throw NoSuchMethodException("Method $methodName with ${args.size} params not found in ${clazz.name}")
    }

    fun callStaticMethod(clazz: Class<*>, methodName: String, vararg args: Any?): Any? {
        val key = Triple(clazz, methodName, args.size)
        staticMethodCache[key]?.let { return it.invoke(null, *args) }

        for (m in clazz.declaredMethods) {
            if (m.name == methodName && m.parameterTypes.size == args.size && Modifier.isStatic(m.modifiers)) {
                m.isAccessible = true
                staticMethodCache.putIfAbsent(key, m)
                return m.invoke(null, *args)
            }
        }
        for (m in clazz.methods) {
            if (m.name == methodName && m.parameterTypes.size == args.size && Modifier.isStatic(m.modifiers)) {
                m.isAccessible = true
                staticMethodCache.putIfAbsent(key, m)
                return m.invoke(null, *args)
            }
        }
        throw NoSuchMethodException("Static method $methodName not found in ${clazz.name}")
    }
}
