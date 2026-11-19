package icu.nullptr.hidemyapplist.xposed

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object XposedHelpers {
    fun findClass(className: String, classLoader: ClassLoader? = null): Class<*> =
        XposedCompat.findClass(className, classLoader)

    fun findField(clazz: Class<*>, fieldName: String): Field {
        var current: Class<*>? = clazz
        while (current != null) {
            for (f in current.declaredFields) {
                if (f.name == fieldName) {
                    f.isAccessible = true
                    return f
                }
            }
            current = current.superclass
        }
        throw NoSuchFieldException("Field $fieldName not found in ${clazz.name}")
    }

    fun getObjectField(obj: Any, fieldName: String): Any? =
        findField(obj.javaClass, fieldName).get(obj)

    fun getIntField(obj: Any, fieldName: String): Int =
        findField(obj.javaClass, fieldName).getInt(obj)

    fun getBooleanField(obj: Any, fieldName: String): Boolean =
        findField(obj.javaClass, fieldName).getBoolean(obj)

    fun setBooleanField(obj: Any, fieldName: String, value: Boolean) =
        findField(obj.javaClass, fieldName).setBoolean(obj, value)

    fun getStaticIntField(clazz: Class<*>, fieldName: String): Int {
        val f = findField(clazz, fieldName)
        if (!Modifier.isStatic(f.modifiers)) {
            throw IllegalArgumentException("Field $fieldName is not static in ${clazz.name}")
        }
        return f.getInt(null)
    }

    fun callMethod(obj: Any, methodName: String, vararg args: Any?): Any? {
        val clazz = obj.javaClass
        var current: Class<*>? = clazz
        while (current != null) {
            for (m in current.declaredMethods) {
                if (m.name == methodName && m.parameterTypes.size == args.size) {
                    m.isAccessible = true
                    return m.invoke(obj, *args)
                }
            }
            current = current.superclass
        }
        throw NoSuchMethodException("Method $methodName with ${args.size} params not found in ${clazz.name}")
    }

    fun callStaticMethod(clazz: Class<*>, methodName: String, vararg args: Any?): Any? {
        for (m in clazz.declaredMethods) {
            if (m.name == methodName && m.parameterTypes.size == args.size && Modifier.isStatic(m.modifiers)) {
                m.isAccessible = true
                return m.invoke(null, *args)
            }
        }
        throw NoSuchMethodException("Static method $methodName not found in ${clazz.name}")
    }
}
