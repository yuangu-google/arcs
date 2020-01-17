package arcs.sdk.android.dev.service.demo

import android.app.assist.AssistStructure.ViewNode

import arcs.sdk.android.dev.api.Collection
import arcs.sdk.android.dev.api.Handle
import arcs.sdk.android.dev.api.ParticleBase
import arcs.sdk.android.dev.api.PortableJson

class AutofillParticle internal constructor(private val node: ViewNode) : ParticleBase() {

    private val jsonRequest: PortableJson
        get() {
            val request = jsonParser.emptyObject()

            val hints = node.autofillHints
            if (hints != null && hints.size > 0) {
                request.put("hint", hints[0])
            }
            return request
        }

    override fun setHandles(handleByName: Map<String, Handle>) {
        super.setHandles(handleByName)

        val requestHandle = handleByName["request"] as Collection?
        // Fill in the request handle so the rest of the recipe can use the data.
        // NOTE: request is an `out` handle, it doesn't get onHandleSync calls.
        requestHandle!!.store(jsonParser.emptyObject().put("rawData", jsonRequest))
    }

    override fun providesSlot(): Boolean {
        return true
    }
}
