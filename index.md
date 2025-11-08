# Decoroutinator playground
<script
    src="https://unpkg.com/kotlin-playground@1"
    data-selector=".kotlin-code"
    data-server="https://api.decoroutinator.reformator.dev/decoroutinator-kotlin-compiler"
    data-version="1.9.23">
</script>
<label for="recoveryType">Stack trace recovery method</label>
<select id="recoveryType">
    <option value="DECOROUTINATOR">Decoroutinator</option>
    <option value="STDLIB">Standard Kotlin stack trace recovery</option>
    <option value="NONE">Without recovery</option>
    <option value="FULL">Decoroutinator and Kotlin stack trace recovery</option>
</select>
<script>
    const originalFetch = window.fetch;
    window.fetch = function(resource, init) {
        let url = resource instanceof Request ? resource.url : resource;
        const u = new URL(url, location.origin);
        u.searchParams.set("recoveryType", document.getElementById("recoveryType").value);
        return originalFetch.call(this, u.toString(), init);
    };

    const originalOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url, ...rest) {
        const u = new URL(url, location.origin);
        u.searchParams.set("recoveryType", document.getElementById("recoveryType").value);
        return originalOpen.call(this, method, u.toString(), ...rest);
    };
</script>
<div class="kotlin-code">
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

suspend fun fun1() {
    delay(10)
    throw Exception("exception at ${System.currentTimeMillis()}")
}

suspend fun fun2() {
    fun1()
    delay(10)
}

suspend fun fun3() {
    fun2()
    delay(10)
}

fun main() {
    runBlocking {
        fun3()
    }
}
</div>
