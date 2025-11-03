//file:noinspection GrPackage

package dev.reformator.stacktracedecoroutinator.gradleplugin.groovy

import dev.reformator.stacktracedecoroutinator.gradleplugin.GroovyDslInitializer
import org.gradle.api.Project
import org.gradle.kotlin.dsl.ApiGradlePluginDecoroutinatorKt
import org.jetbrains.annotations.NotNull


class GroovyDslInitializerImpl implements GroovyDslInitializer {
    @Override
    void initGroovyDsl(@NotNull Project target) {
        target.metaClass.decoroutinatorAndroidProGuardRules = { ->
            ApiGradlePluginDecoroutinatorKt.decoroutinatorAndroidProGuardRules(target)
        }

        target.metaClass.decoroutinatorCommon = { ->
            ApiGradlePluginDecoroutinatorKt.decoroutinatorCommon()
        }

        target.metaClass.decoroutinatorJvmRuntime = { ->
            ApiGradlePluginDecoroutinatorKt.decoroutinatorJvmRuntime()
        }

        target.metaClass.decoroutinatorAndroidRuntime = { ->
            ApiGradlePluginDecoroutinatorKt.decoroutinatorAndroidRuntime()
        }

        target.metaClass.decoroutinatorRegularMethodHandleInvoker = { ->
            ApiGradlePluginDecoroutinatorKt.decoroutinatorRegularMethodHandleInvoker()
        }

        target.metaClass.decoroutinatorAndroidMethodHandleInvoker = { ->
            ApiGradlePluginDecoroutinatorKt.decoroutinatorAndroidMethodHandleInvoker()
        }

        target.metaClass.decoroutinatorJvmMethodHandleInvoker = { ->
            ApiGradlePluginDecoroutinatorKt.decoroutinatorJvmMethodHandleInvoker()
        }

        target.metaClass.decoroutinatorRuntimeSettings = { ->
            ApiGradlePluginDecoroutinatorKt.decoroutinatorRuntimeSettings()
        }
    }
}
