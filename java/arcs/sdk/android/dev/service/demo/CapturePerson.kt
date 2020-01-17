package arcs.sdk.android.dev.service.demo

import arcs.sdk.android.dev.api.Collection
import arcs.sdk.android.dev.api.Handle
import arcs.sdk.android.dev.api.ParticleBase

internal class CapturePerson : ParticleBase() {

    override fun setHandles(handleByName: Map<String, Handle>) {
        super.setHandles(handleByName)
        (getHandle("people") as Collection)
                .toList()
                .thenAccept { model -> generatePerson((model?.length ?: -1) + 1) }
    }

    fun generatePerson(index: Int) {
        val person = jsonParser
                .emptyObject()
                .put("firstName", "Foo$index")
                .put("lastName", "Bar$index")
                .put("password", "password-$index")
                .put("phone", "$index-phone")
                .put("postalAddress", String.format("%d-%d Main st.", index, index))
        (getHandle("people") as Collection).store(jsonParser.emptyObject().put("rawData", person))
    }
}
