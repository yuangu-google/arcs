package wasm.kotlin.tests.arcs

import arcs.Handle
import arcs.Particle
import arcs.Singleton
import arcs.WasmAddress
import kotlin.native.internal.ExportForCppRuntime

class RenderTest : Particle() {
    private val flags = Singleton { Test_RenderFlags() }

    init {
        registerHandle("flags", flags)
    }

    override fun getTemplate(slotName: String): String {
        return "abc"
    }

    override fun populateModel(slotName: String, model: Map<String, String>): Map<String, String> {
        return mapOf("foo" to "bar")
    }

    override fun onHandleUpdate(handle: Handle) {
        flags.get()?.let { renderOutput() }
    }
}

@Retain
@ExportForCppRuntime("_newRenderTest")
fun constructRenderTest(): WasmAddress = RenderTest().toWasmAddress()