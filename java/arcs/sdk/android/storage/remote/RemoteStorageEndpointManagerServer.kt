package arcs.sdk.android.storage.remote

import arcs.android.storage.decode
import arcs.core.crdt.CrdtData
import arcs.core.crdt.CrdtOperationAtTime
import arcs.core.storage.ProxyMessage
import arcs.core.storage.StorageEndpoint
import arcs.core.storage.StorageEndpointManager
import arcs.core.storage.StorageKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel

/**
 * A [StorageEndpointManager] that creates communicates with another [StorageEndpoint] via
 * byte-encoded Protobufs over a set of [Channel]s.
 */
class RemoteStorageEndpointManagerServer<Data : CrdtData, Op : CrdtOperationAtTime, T>(
  private val outputChannel: BroadcastChannel<ByteArray>,
  private val inputChannel: BroadcastChannel<ByteArray>,
  private val endpointManager: StorageEndpointManager,
  private val scope: CoroutineScope
) {
  val protoChannel = ProtoChannel<Data, Op, T>(
    outputChannel,
    inputChannel,
    1,
    msgIdProvider = { 1 }
  )
  var remoteEndpoints: MutableMap<Int, StorageEndpoint<Data, Op, T>?> = mutableMapOf()
  var remoteEndpointClients: MutableMap<StorageKey, MutableSet<Int>> = mutableMapOf()
  var remoteClientToKey: MutableMap<Int, StorageKey> = mutableMapOf()

  fun start() {
    protoChannel.startServer(scope) { message ->
      when {
        message.hasConnectMessage() -> {
          val channelId = message.channelId
          val storeOptions = message.connectMessage.storeOptions.decode()
          remoteClientToKey.put(channelId, storeOptions.storageKey)
          val clients = remoteEndpointClients.getOrPut(storeOptions.storageKey) {
            mutableSetOf<Int>()
          }
          clients.add(channelId)
          remoteEndpoints.getOrPut(channelId) {
            val endpoint = endpointManager.get<Data, Op, T>(
              storeOptions
            ) { proxyMessage ->
              remoteEndpointClients.get(storeOptions.storageKey)?.forEach { clientId ->
                println("Sending proxy message to client $clientId, $proxyMessage")
                protoChannel.sendProxyMessageToClient(clientId, proxyMessage)
              }
            }
            println("Connected $channelId, ${message.messageId}")
            protoChannel.sendAck(channelId, message.messageId)
            endpoint
          }
        }
        message.hasIdleMessage() -> {
          remoteEndpoints.get(message.channelId)?.idle()
          protoChannel.sendAck(message.channelId, message.messageId)
        }
        message.hasCloseMessage() -> {
          println("calling close channelId=${message.channelId} id=${message.messageId}")
          val clients = remoteEndpointClients.get(remoteClientToKey.get(message.channelId))
          clients?.remove(message.channelId)
          if (clients?.isEmpty() ?: false) {
            println("closing endpoint")
            remoteEndpoints.get(message.channelId)?.close() ?: println("missing endpoint close")
          }
          protoChannel.sendAck(message.channelId, message.messageId)
        }
        message.hasProxyMessage() -> {
          remoteEndpoints.get(message.channelId)?.onProxyMessage(
            message.proxyMessage.decode() as ProxyMessage<Data, Op, T>
          )
          protoChannel.sendAck(message.channelId, message.messageId)
        }
      }
    }
  }
}
