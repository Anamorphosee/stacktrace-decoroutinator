import org.junit.Test

class TestLocalFile: dev.reformator.stacktracedecoroutinator.gradlepluginandroidtests.TestLocalFile() {
    @Test
    override fun localTest() {
        super.localTest()
    }
}

class TailCallDeoptimizeTest: dev.reformator.stacktracedecoroutinator.gradlepluginandroidtests.TailCallDeoptimizeTest() {
    @Test
    override fun performBasic() {
        super.performBasic()
    }

    @Test
    override fun performInterfaceWithDefaultMethodImpl() {
        super.performInterfaceWithDefaultMethodImpl()
    }
}

class DebugProbesTest: dev.reformator.stacktracedecoroutinator.gradlepluginandroidtests.DebugProbesTest() {
    @Test
    override fun performDebugProbes() {
        super.performDebugProbes()
    }
}

class RuntimeTest: dev.reformator.stacktracedecoroutinator.test.RuntimeTest() {
    @Test
    override fun inlineTransformedClassForKotlinc() {
        super.inlineTransformedClassForKotlinc()
    }

    @Test
    override fun basic() {
        super.basic()
    }

    @Test
    override fun overloadedMethods() {
        super.overloadedMethods()
    }

    @Test
    override fun resumeWithException() {
        super.resumeWithException()
    }

    @Test
    override fun resumeDoubleException() {
        super.resumeDoubleException()
    }

    @Test
    override fun testLoadSelfDefinedClass() {
        super.testLoadSelfDefinedClass()
    }

    @Test
    override fun testSuspendCrossinlineInDifferentFile() {
        super.testSuspendCrossinlineInDifferentFile()
    }

    @Test
    override fun loadInterfaceWithSuspendFunWithDefaultImpl() {
        super.loadInterfaceWithSuspendFunWithDefaultImpl()
    }

    @Test
    override fun flowSingle() {
        super.flowSingle()
    }

    @Test
    override fun concurrentTest() {
        super.concurrentTest()
    }
}

class PerformanceTest: dev.reformator.stacktracedecoroutinator.test.PerformanceTest() {
    @Test
    override fun depth10() {
        super.depth10()
    }

    @Test
    override fun depth50() {
        super.depth50()
    }

    @Test
    override fun depth100() {
        super.depth100()
    }

    @Test
    override fun depth500() {
        super.depth500()
    }
}
