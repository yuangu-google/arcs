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

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import arcs.android.host.AndroidManifestHostRegistry
import arcs.core.allocator.Allocator
import arcs.core.host.EntityHandleManager
import arcs.core.host.HostRegistry
import arcs.core.host.SimpleSchedulerProvider
import arcs.jvm.util.JvmTime
import arcs.sdk.android.storage.AndroidStorageServiceEndpointManager
import arcs.sdk.android.storage.service.DefaultConnectionFactory
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.client.Socket.EVENT_CONNECT
import io.socket.client.Socket.EVENT_DISCONNECT
import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

  private val storageEndpointManager = AndroidStorageServiceEndpointManager(
    scope,
    DefaultConnectionFactory(this)
  )

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.main_activity)

    scope.launch {
      hostRegistry = AndroidManifestHostRegistry.create(this@DemoActivity)

      allocator = Allocator.create(
        hostRegistry,
        EntityHandleManager(
          time = JvmTime,
          scheduler = schedulerProvider("personArc"),
          storageEndpointManager = storageEndpointManager

        )
      )

      findViewById<Button>(R.id.person_test).setOnClickListener {}
    }

    connectToSignallingServer();
    initializePeerConnectionFactory();
    initializePeerConnections();
  }

  override fun onDestroy() {
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
        .on("created") { Log.d(TAG, "connectToSignallingServer: created") }
        .on("full") { Log.d(TAG, "connectToSignallingServer: full") }
        .on("join") {
          Log.d(TAG, "connectToSignallingServer: join")
          isChannelReady = true
        }
        .on("joined") {
          Log.d(TAG, "connectToSignallingServer: joined")
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

  private fun initializePeerConnections() {
    peerConnection = createPeerConnection(factory)
    sendChannel = peerConnection.createDataChannel("sendDataChannel", Init())
    sendChannel.registerObserver(object : DataChannel.Observer {
      override fun onBufferedAmountChange(l: Long) {}
      override fun onStateChange() {
        Log.d(
          TAG,
          "local data channel onStateChange: " + sendChannel.state().toString()
        )
        if (sendChannel.state() == DataChannel.State.OPEN) {
          val data = stringToByteBuffer("-s" + "hello world")
          sendChannel.send(DataChannel.Buffer(data, false))
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
        Log.d(TAG, "onIceConnectionChange: ")
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
        Log.d(TAG, "onDataChannel: ")
        recvChannel.registerObserver(object : DataChannel.Observer {
          override fun onBufferedAmountChange(l: Long) {}
          override fun onStateChange() {}
          override fun onMessage(buffer: DataChannel.Buffer) {
            Log.d(
              TAG,
              "data channel onMessage: got message" + byteBufferToString(buffer.data)
            )
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


  private fun stringToByteBuffer(msg: String): ByteBuffer? {
    return ByteBuffer.wrap(msg.toByteArray(Charset.defaultCharset()))
  }

  private fun byteBufferToString(buffer: ByteBuffer): String? {
    val bytes: kotlin.ByteArray
    if (buffer.hasArray()) {
      bytes = buffer.array()
    } else {
      bytes = kotlin.ByteArray(buffer.remaining())
      buffer.get(bytes)
    }
    return String(bytes, Charset.defaultCharset())
  }

  companion object {
    const val TAG = "Demo"
  }
}
