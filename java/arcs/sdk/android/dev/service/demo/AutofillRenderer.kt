package arcs.sdk.android.dev.service.demo

import android.content.Context
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews

import java.util.HashMap
import java.util.function.Consumer

import javax.inject.Inject

import arcs.sdk.android.dev.api.PortableJson
import arcs.sdk.android.dev.api.UiRenderer

class AutofillRenderer @Inject
internal constructor(private val context: Context) : UiRenderer {

    private val slotById = HashMap<String, SlotInfo>()

    class SlotInfo internal constructor(internal val autofillId: AutofillId, internal val callback: Consumer<FillResponse>)

    override fun render(packet: PortableJson): Boolean {
        val slotId = packet.getString(CONTAINER_SLOT_ID_FIELD)
        val slotInfo = slotById[slotId] ?: return false

        // Trigger autofill callback.
        val content = packet.getObject(CONTENT_FIELD)
        val suggestion = content.getString(CANDIDATE_FIELD)
        val dataset = Dataset.Builder()
        dataset.setValue(
                slotInfo.autofillId, AutofillValue.forText(suggestion), createRemoteView(suggestion))

        val fillResponse = FillResponse.Builder().addDataset(dataset.build()).build()
        slotInfo.callback.accept(fillResponse)

        // Remove slot info.
        slotById.remove(slotId)
        return true
    }

    fun addCallback(slotId: String, autofillId: AutofillId, callback: Consumer<FillResponse>) {
        if (slotById.containsKey(slotId)) {
            throw IllegalArgumentException("Callback already exists for $slotId")
        }
        slotById[slotId] = SlotInfo(autofillId, callback)
    }

    private fun createRemoteView(contents: String): RemoteViews {
        val view = RemoteViews(context.packageName, R.layout.autofill_result)
        view.setTextViewText(R.id.autofill_result_text, contents)
        return view
    }

    companion object {

        private val CONTAINER_SLOT_ID_FIELD = "containerSlotId"
        private val CONTENT_FIELD = "content"
        private val CANDIDATE_FIELD = "candidate"
    }
}
