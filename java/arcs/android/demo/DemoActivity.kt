/*
 * Copyright 2020 Google LLC.
 *
 * This code may only be used under the BSD style license found at
 * http://polymer.github.io/LICENSE.txt
 *
 * Code distributed by Google as part of this project is also subject to an additional IP rights
 * grant found at
 * http://polymer.github.io/PATENTS.txt
 */

package arcs.android.demo

import android.app.Application
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import arcs.android.storage.database.AndroidSqliteDatabaseManager
import arcs.core.allocator.Allocator
import arcs.core.allocator.Arc
import arcs.core.crdt.CrdtData
import arcs.core.crdt.CrdtOperationAtTime
import arcs.core.host.AbstractArcHost
import arcs.core.host.EntityHandleManager
import arcs.core.host.HostRegistry
import arcs.core.host.ParticleConstructor
import arcs.core.host.ParticleRegistration
import arcs.core.host.ParticleState
import arcs.core.host.SchedulerProvider
import arcs.core.host.SimpleSchedulerProvider
import arcs.core.host.toParticleIdentifier
import arcs.core.storage.DirectStorageEndpointManager
import arcs.core.storage.StorageEndpointManager
import arcs.core.storage.StoreManager
import arcs.core.storage.StoreWriteBack
import arcs.core.storage.api.DriverAndKeyConfigurator
import arcs.core.storage.driver.RamDisk
import arcs.jvm.host.ExplicitHostRegistry
import arcs.jvm.util.JvmTime
import arcs.sdk.Particle
import arcs.sdk.android.storage.remote.RemoteStorageEndpointManager
import arcs.sdk.android.storage.remote.RemoteStorageEndpointManagerServer
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.client.Socket.EVENT_CONNECT
import io.socket.client.Socket.EVENT_DISCONNECT
import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.DataChannel.Init
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceConnectionState
import org.webrtc.PeerConnection.IceGatheringState
import org.webrtc.PeerConnection.IceServer
import org.webrtc.PeerConnection.RTCConfiguration
import org.webrtc.PeerConnection.SignalingState
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import org.webrtc.SessionDescription.Type.ANSWER
import org.webrtc.SessionDescription.Type.OFFER

/** Entry UI to launch Arcs demo. */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoActivity : AppCompatActivity() {

  private var isChannelReady = false
  private var isStarted = false

  private lateinit var socket: Socket
  private lateinit var peerConnection: PeerConnection
  private lateinit var sendChannel: DataChannel
  private lateinit var factory: PeerConnectionFactory

  private val coroutineContext: CoroutineContext = Job() + Dispatchers.Main
  private val scope: CoroutineScope = CoroutineScope(coroutineContext)
  private val schedulerProvider = SimpleSchedulerProvider(Dispatchers.Default)

  /**
   * Recipe hand translated from 'person.arcs'
   */
  private lateinit var allocator: Allocator
  private lateinit var hostRegistry: HostRegistry
  private lateinit var chatParticle: ChatParticle
  private lateinit var arcHost: ChatHost
  private lateinit var arc: Arc
  private lateinit var storageEndpointManager: StorageEndpointManager


  private lateinit var input: EditText
  private lateinit var sendBtn: Button
  private lateinit var msgList: RecyclerView
  private lateinit var msgAdapter: RecyclerView.Adapter<*>
  private lateinit var msgLayoutManager: RecyclerView.LayoutManager
  private val messages: ArrayList<Message> = ArrayList()
  val randomNumber = Random.nextLong(Long.MAX_VALUE)

  val chatParticleCtor: ParticleConstructor = { plan ->
    ChatParticle(::receiveMessage)
  }

  fun receiveMessage(msg: String, particle: Particle) {
    runOnUiThread {
      Log.d(TAG, "Updating message")
      messages.add(Message(msg, isRecv = true))
      msgAdapter.notifyDataSetChanged()
    }
  }


  val outputChannel = BroadcastChannel<ByteArray>(1000)
  val inputChannel = BroadcastChannel<ByteArray>(1000)

    override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.main_activity)

    scope.launch {


      input = findViewById(R.id.input)
      msgLayoutManager = LinearLayoutManager(this@DemoActivity)
      msgAdapter = MsgAdapter()

      msgList = findViewById(R.id.msg_list)
      msgList.layoutManager = msgLayoutManager
      msgList.adapter = msgAdapter

      sendBtn = findViewById(R.id.send_btn)
      sendBtn.setOnClickListener {
        if (sendChannel.state() == DataChannel.State.OPEN) {
          val text = input.text.toString()
          if (text.isNotEmpty()) {
            scope.launch {
              chatParticle.sendMessage(text)
            }
//            val data = stringToByteBuffer(text)
//            sendChannel.send(DataChannel.Buffer(data, false))
            Log.d(TAG, "Sending updating message")
            messages.add(Message(text, isRecv = false))
            msgAdapter.notifyDataSetChanged()
          }
        }
      }
    }

    setToolbar("Connecting to signal server")
    connectToSignallingServer();

  }

  override fun onDestroy() {
    socket.disconnect()
    scope.cancel()
    super.onDestroy()
  }

  private fun testPersonRecipe() {
    scope.launch {
      val arcId = allocator.startArcForPlan(PersonRecipePlan).id
      allocator.stopArc(arcId)
    }
  }

  private fun connectToSignallingServer() {
    try {
      socket = IO.socket("https://evening-stream-20946.herokuapp.com/")
      socket
        .on(EVENT_CONNECT) {
          Log.d(TAG, "connectToSignallingServer: connect")
          socket.emit("create or join", "foo")
        }
        .on(EVENT_DISCONNECT) { Log.d(TAG, "connectToSignallingServer: disconnect") }
        .on("ipaddr") { Log.d(TAG, "connectToSignallingServer: ipaddr") }
        .on("created") {
          Log.d(TAG, "connectToSignallingServer: created")
          setToolbar("Waiting for others to join chat")
        }
        .on("full") { Log.d(TAG, "connectToSignallingServer: full") }
        .on("join") {
          Log.d(TAG, "connectToSignallingServer: join")
          setToolbar("In chat")
          isChannelReady = true
        }
        .on("joined") {
          Log.d(TAG, "connectToSignallingServer: joined")
          setToolbar("In chat")
          isChannelReady = true
          maybeStart()
        }
        .on("ready") { Log.d(TAG, "connectToSignallingServer: room is ready") }
        .on("message") { args: Array<Any> ->
          Log.d(TAG, "connectToSignallingServer: got a message")
          try {
            val message = args[0] as JSONObject
            Log.d(
              TAG,
              "connectToSignallingServer: got message $message"
            )
            if (message.getString("type") == "offer") {
              peerConnection.setRemoteDescription(
                SimpleSdpObserver(),
                SessionDescription(
                  OFFER,
                  message.getString("sdp")
                )
              )
              doAnswer()
            } else if (message.getString("type") == "answer" && isStarted) {
              peerConnection.setRemoteDescription(
                SimpleSdpObserver(),
                SessionDescription(
                  ANSWER,
                  message.getString("sdp")
                )
              )
            } else if (message.getString("type") == "candidate" && isStarted) {
              Log.d(
                TAG,
                "connectToSignallingServer: receiving candidates"
              )
              val candidate = IceCandidate(
                message.getString("id"),
                message.getInt("label"),
                message.getString("candidate")
              )
              peerConnection.addIceCandidate(candidate)
            }
          } catch (e: JSONException) {
            e.printStackTrace()
          }
        }
      Log.d(TAG, "about to connect")
      initializePeerConnectionFactory();
      initializePeerConnections();
      socket.connect()
    } catch (e: URISyntaxException) {
      Log.d(TAG, "exception")
      e.printStackTrace()
    }
  }

  private fun doAnswer() {
    peerConnection.createAnswer(object : SimpleSdpObserver() {
      override fun onCreateSuccess(sessionDescription: SessionDescription?) {
        peerConnection.setLocalDescription(SimpleSdpObserver(), sessionDescription)
        val message = JSONObject()
        try {
          message.put("type", "answer")
          message.put("sdp", sessionDescription!!.description)
          sendMessage(message)
        } catch (e: JSONException) {
          e.printStackTrace()
        }
      }
    }, MediaConstraints())
  }

  private fun maybeStart() {
    Log.d(TAG, "maybeStart: $isStarted $isChannelReady")
    if (!isStarted && isChannelReady) {
      isStarted = true
      doCall()
    }
  }

  private fun doCall() {
    val sdpMediaConstraints = MediaConstraints()
    peerConnection.createOffer(object : SimpleSdpObserver() {
      override fun onCreateSuccess(sessionDescription: SessionDescription?) {
        Log.d(TAG, "onCreateSuccess: ")
        peerConnection.setLocalDescription(SimpleSdpObserver(), sessionDescription)
        val message = JSONObject()
        try {
          message.put("type", "offer")
          message.put("sdp", sessionDescription!!.description)
          sendMessage(message)
        } catch (e: JSONException) {
          e.printStackTrace()
        }
      }
    }, sdpMediaConstraints)
  }

  private fun sendMessage(message: Any) {
    socket.emit("message", message)
  }

  private fun initializePeerConnectionFactory() {
    PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true)
    factory = PeerConnectionFactory(null)
  }

  enum class ClientServer { Client, Server, Unknown }
  var clientServer: ClientServer = ClientServer.Unknown

  private fun initializePeerConnections() {
    peerConnection = createPeerConnection(factory)
    sendChannel = peerConnection.createDataChannel("sendDataChannel", Init())
    sendChannel.registerObserver(object : DataChannel.Observer {
      override fun onBufferedAmountChange(l: Long) {}
      override fun onStateChange() {
        Log.d(TAG, "local data channel onStateChange: " + sendChannel.state().toString())
        sendChannel.send(DataChannel.Buffer(stringToByteBuffer(randomNumber.toString()), false))
        Log.d(TAG, "sent randomNumber $randomNumber")

        runOnUiThread {
          if (clientServer != ClientServer.Unknown) {
            sendBtn.isEnabled = sendChannel.state() == DataChannel.State.OPEN
            if (sendBtn.isEnabled) {
              sendBtn.alpha = 1.0f
            } else {
              sendBtn.alpha = 0.5f
            }
          }
        }
      }

      override fun onMessage(buffer: DataChannel.Buffer) {}
    })
  }

  private fun createPeerConnection(factory: PeerConnectionFactory): PeerConnection {
    val iceServers: ArrayList<IceServer> = ArrayList()
    iceServers.add(IceServer("stun:stun.l.google.com:19302"))
    val rtcConfig = RTCConfiguration(iceServers)
    val pcConstraints = MediaConstraints()
    val pcObserver: PeerConnection.Observer = object : PeerConnection.Observer {
      override fun onSignalingChange(signalingState: SignalingState) {
        Log.d(TAG, "onSignalingChange: ")
      }

      override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {
        Log.d(TAG, "onIceConnectionChange: " + iceConnectionState.name)
      }

      override fun onIceConnectionReceivingChange(b: Boolean) {
        Log.d(TAG, "onIceConnectionReceivingChange: ")
      }

      override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
        Log.d(TAG, "onIceGatheringChange: ")
      }

      override fun onIceCandidate(iceCandidate: IceCandidate) {
        Log.d(TAG, "onIceCandidate: ")
        val message = JSONObject()
        try {
          message.put("type", "candidate")
          message.put("label", iceCandidate.sdpMLineIndex)
          message.put("id", iceCandidate.sdpMid)
          message.put("candidate", iceCandidate.sdp)
          Log.d(
            TAG,
            "onIceCandidate: sending candidate $message"
          )
          sendMessage(message)
        } catch (e: JSONException) {
          e.printStackTrace()
        }
      }

      override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
        Log.d(TAG, "onIceCandidatesRemoved: ")
      }

      override fun onAddStream(mediaStream: MediaStream) {
        Log.d(TAG, "onAddStream: ")
      }

      override fun onRemoveStream(mediaStream: MediaStream) {
        Log.d(TAG, "onRemoveStream: ")
      }


      override fun onDataChannel(recvChannel: DataChannel) {
        Log.d(TAG, "onDataChannel2: ")
        recvChannel.registerObserver(object : DataChannel.Observer {
          override fun onBufferedAmountChange(l: Long) {}
          override fun onStateChange() {}
          override fun onMessage(buffer: DataChannel.Buffer) {
            Log.d(TAG, "onMessage called with DataChannel")
            val buf = byteBufferToByteArray(buffer.data)
            val msg = if (!buffer.binary) byteArrayToString(buf) else ""

            if (clientServer == ClientServer.Unknown) {
              val hisNumber = msg.trim().toLong()
              Log.d(TAG, "Got hisNumber $hisNumber, myNumber $randomNumber")

              if (randomNumber > hisNumber) {
                Log.d(TAG, "Starting server")
                clientServer = ClientServer.Server
                storageEndpointManager =  DirectStorageEndpointManager(
                  StoreManager(scope) { protocol ->
                    StoreWriteBack(
                      protocol,
                      Channel.UNLIMITED,
                      forceEnable = true,
                      scope = scope
                    )
                  }
                )

              } else {
                Log.d(TAG, "Starting client")
                clientServer = ClientServer.Client
                storageEndpointManager = RemoteStorageEndpointManager(
                  outputChannel,
                  inputChannel,
                  scope
                )
              }


              scope.launch {

                RamDisk.clear()
                // Initializing the environment...
                WorkManagerTestInitHelper.initializeTestWorkManager(this@DemoActivity)

                // Set up the Database manager, drivers, and keys/key-parsers.
                val dbManager = AndroidSqliteDatabaseManager(this@DemoActivity).also {
                  // Be sure we always start with a fresh, empty database.
                  it.resetAll()
                }

                DriverAndKeyConfigurator.configure(dbManager)
                arcHost = ChatHost(
                  coroutineContext,
                  schedulerProvider,
                  storageEndpointManager,
                  ChatParticle::class.toParticleIdentifier() to chatParticleCtor
                )
                hostRegistry = ExplicitHostRegistry().also {
                  it.registerHost(arcHost)
                }
                allocator = Allocator.create(
                  hostRegistry,
                  EntityHandleManager(
                    time = JvmTime,
                    scheduler = schedulerProvider("chatArc"),
                    storageEndpointManager = storageEndpointManager
                  )
                )
                if (clientServer == ClientServer.Server) {
                  Log.d(TAG, "Starting RemoteStorageEndpointManagerServer")

                  RemoteStorageEndpointManagerServer<CrdtData, CrdtOperationAtTime, Any?>(
                    outputChannel,
                    inputChannel,
                    storageEndpointManager!!,
                    scope
                  ).start()
                }

                Log.d(TAG, "Starting outputChannel")

                outputChannel.openSubscription().consumeAsFlow().onEach { msg ->
                  sendChannel.send(DataChannel.Buffer(ByteBuffer.wrap(msg),true))
                }.launchIn(scope)

                Log.d(TAG, "Starting Arc")
                arc = allocator.startArcForPlan(if (clientServer == ClientServer.Client) ChatRecipeOnePlan else ChatRecipeTwoPlan)
                Log.d(TAG, "Waiting for Arc")
                arc.waitForStart()
                Log.d(TAG, "Getting Particle")
                chatParticle = arcHost.getParticle<ChatParticle>(arc.id.toString(), "ChatParticle")
                runOnUiThread {
                  sendBtn.isEnabled = true
                  sendBtn.alpha = 1.0f
                }
              }
            } else {
              Log.d(TAG, "Bytes received sending to input channel ${buffer.data}")
              inputChannel.offer(buf)
            }

            Log.d(TAG, "data channel onMessage: got message $msg")
          }
        })
      }

      override fun onRenegotiationNeeded() {
        Log.d(TAG, "onRenegotiationNeeded: ")
      }

      override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
        Log.d(TAG, "onAddTrack: ")
      }
    }
    return factory.createPeerConnection(rtcConfig, pcConstraints, pcObserver)
  }


  private fun stringToByteBuffer(msg: String): ByteBuffer {
    return ByteBuffer.wrap(msg.toByteArray(Charset.defaultCharset()))
  }

  private fun byteBufferToByteArray(buffer: ByteBuffer): ByteArray {
    val bytes: kotlin.ByteArray
    if (buffer.hasArray()) {
      bytes = buffer.array()
    } else {
      bytes = kotlin.ByteArray(buffer.remaining())
      buffer.get(bytes)
    }
    return bytes
  }

  private fun byteArrayToString(bytes: ByteArray): String {
    return String(bytes, Charset.defaultCharset())
  }

  private fun byteBufferToString(buffer: ByteBuffer): String {
    val bytes: kotlin.ByteArray
    if (buffer.hasArray()) {
      bytes = buffer.array()
    } else {
      bytes = kotlin.ByteArray(buffer.remaining())
      buffer.get(bytes)
    }
    return String(bytes, Charset.defaultCharset())
  }

  inner class MsgAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    inner class RecvViewHolder(val view: View) : RecyclerView.ViewHolder(view)
    inner class SendViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
      // create a new view
      return if (viewType == 0) {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.message_recv, parent, false)
        RecvViewHolder(view)
      } else {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.message_send, parent, false)
        SendViewHolder(view)
      }
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
      if (getItemViewType(position) === 0) {
        (holder as RecvViewHolder).view.findViewById<TextView>(R.id.content).text =
          messages[position].text
      } else {
        (holder as SendViewHolder).view.findViewById<TextView>(R.id.content).text =
          messages[position].text
      }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = messages.size

    override fun getItemViewType(position: Int): Int {
      return if(messages[position].isRecv) 0 else 1
    }
  }

  private fun setToolbar(text: String) {
    runOnUiThread {
      supportActionBar?.title = text
    }
  }

  companion object {
    const val TAG = "Demo"
  }
}

class ChatParticle(val receiver: (String, Particle) -> Unit) : AbstractChatParticle() {

  override fun onFirstStart() {
    Log.d(DemoActivity.TAG, "ChatParticle first start")
  }
  override fun onReady() {
    Log.d(DemoActivity.TAG, "ChatParticle ready")
    handles.inMessage.onUpdate { delta ->
      Log.d(DemoActivity.TAG, "ChatParticle onUpdate $delta")
      if (delta.new != null) {
        receiver(handles.inMessage.fetch()?.message ?: "", this)
      }
    }
  }

  suspend fun sendMessage(msg: String) {
    Log.d(DemoActivity.TAG, "ChatParticle send $msg")
    withContext(handles.dispatcher) {
      handles.outMessage.store(Chat(msg))
    }
  }
}

@ExperimentalCoroutinesApi
class ChatHost(
  coroutineContext: CoroutineContext,
  schedulerProvider: SchedulerProvider,
  storageEndpointManager: StorageEndpointManager,
  vararg particleRegistrations: ParticleRegistration
) : AbstractArcHost(
  coroutineContext = coroutineContext,
  updateArcHostContextCoroutineContext = coroutineContext,
  schedulerProvider = schedulerProvider,
  storageEndpointManager = storageEndpointManager,
  initialParticles = *particleRegistrations
) {
  override val platformTime = JvmTime

  @Suppress("UNCHECKED_CAST")
  suspend fun <T> getParticle(arcId: String, particleName: String): T {
    val arcHostContext = requireNotNull(getArcHostContext(arcId)) {
      "ArcHost: No arc host context found for $arcId"
    }
    val particleContext = requireNotNull(
      arcHostContext.particles.first {
        it.planParticle.particleName == particleName
      }
    ) {
      "ArcHost: No particle named $particleName found in $arcId"
    }
    val allowableStartStates = arrayOf(ParticleState.Running, ParticleState.Waiting)
    check(particleContext.particleState in allowableStartStates) {
      "ArcHost: Particle $particleName has failed, or not been started"
    }

    @Suppress("UNCHECKED_CAST")
    return particleContext.particle as T
  }

  override fun toString(): String = "ShowcaseHost"
}
