package icu.nullptr.hidemyapplist.common

inline fun <K, V> MutableMap<K, V>.removeIf(predicate: (K, V) -> Boolean) {
    this.filter { (key, value) -> predicate(key, value) }.forEach { this.remove(it.key) }
}

inline fun <reified K, reified V> MutableMap<K, V>.sync(from: Map<K, V>) {
    clear()
    putAll(from)
}
