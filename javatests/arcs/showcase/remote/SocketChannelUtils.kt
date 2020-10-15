package arcs.showcase.remote

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

fun Socket.asOutputChannel(scope: CoroutineScope): BroadcastChannel<ByteArray> {
  val outputStream = DataOutputStream(this.getOutputStream())
  val outputChannel = BroadcastChannel<ByteArray>(1000)
  outputChannel.openSubscription().consumeAsFlow().onEach { byteArray ->
    outputStream.writeInt(byteArray.size)
    outputStream.write(byteArray, 0, byteArray.size)
  }.launchIn(scope)
  return outputChannel
}

fun Socket.asInputChannel(scope: CoroutineScope): BroadcastChannel<ByteArray> {
  val inputChannel = BroadcastChannel<ByteArray>(1000)
  val inputStream = DataInputStream(this.getInputStream())
  scope.launch {
    while (!Thread.currentThread().isInterrupted) {
      var numBytes = inputStream.readInt()
      val byteArray = ByteArray(numBytes)
      var off = 0
      do {
        off = inputStream.read(byteArray, off, numBytes)
        if (off < -1) {
          break
        }
        numBytes -= off
      } while (numBytes > 0)
      inputChannel.send(byteArray)
    }
  }
  return inputChannel
}
