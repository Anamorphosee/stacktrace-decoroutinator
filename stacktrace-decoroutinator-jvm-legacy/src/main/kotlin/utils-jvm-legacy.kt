package dev.reformator.stacktracedecoroutinator.jvmlegacy

fun ClassLoader.getClassLoadingLock(className: String): Any {
    val method = ClassLoader::class.java.getDeclaredMethod("getClassLoadingLock", String::class.java)
    method.isAccessible = true
    return method.invoke(this, className)
}

fun ClassLoader.getClassIfLoaded(className: String): Class<*>? {
    val method = ClassLoader::class.java.getDeclaredMethod("findLoadedClass", String::class.java)
    method.isAccessible = true
    return synchronized(getClassLoadingLock(className)) {
        method.invoke(this, className) as Class<*>?
    }
}

fun ClassLoader.loadClass(className: String, classBody: ByteArray): Class<*> {
    val method = ClassLoader::class.java.getDeclaredMethod("defineClass", String::class.java,
        ByteArray::class.java, Integer.TYPE, Integer.TYPE)
    method.isAccessible = true
    return synchronized(getClassLoadingLock(className)) {
        method.invoke(this, className, classBody, 0, classBody.size) as Class<*>
    }
}
