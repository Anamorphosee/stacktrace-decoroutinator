---
title: Playground
permalink: /playground/
---

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

<script src="/js/set_up_transfering_of_recovery_type.js"></script>

<div id="code" class="kotlin-code">
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

suspend fun fun1() {
    yield()
    throw Exception("exception at ${System.currentTimeMillis()}")
}

suspend fun fun2() {
    fun1()
}

suspend fun fun3() {
    fun2()
}

fun main() {
    runTest {
        fun3()
    }
}
</div>

<script src="/js/fill_code_from_url_param.js"></script>
