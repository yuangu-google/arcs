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

package arcs.showcase.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import arcs.core.host.ParticleConstructor
import arcs.core.host.toParticleIdentifier
import arcs.sdk.Particle
import com.google.common.truth.Truth.assertThat
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ChatTest {

  fun initChannels(scope: CoroutineScope, socketProvider: () -> Socket) = scope.async {
    socketProvider().let {
      ShowcaseEnvironmentRemoteStorage.ChannelPair(
        it.asOutputChannel(scope),
        it.asInputChannel(scope)
      )
    }
  }

  val chatParticleCtor: ParticleConstructor = { plan ->
    ChatParticle(::receiveMessage)
  }

  @get:Rule
  val env = ShowcaseEnvironmentRemoteStorage(
    60000,
    initChannels(GlobalScope) { ServerSocket(9999).accept() },
    initChannels(GlobalScope) { Socket("127.0.0.1", 9999) },
    ChatParticle::class.toParticleIdentifier() to chatParticleCtor
  )

  val messages: MutableMap<Particle, CompletableDeferred<String>> = mutableMapOf()

  fun receiveMessage(msg: String, particle: Particle) {
    if (particle !in messages) {
      messages.put(particle, CompletableDeferred())
    }
    messages.get(particle)!!.complete(msg)
  }

  suspend fun waitFor(chatParticle: Particle): String {
    val msg = messages.getOrPut(chatParticle) { CompletableDeferred<String>() }.await()
    messages.put(chatParticle, CompletableDeferred())
    return msg
  }

  @Test
  fun chatTest() = runBlocking {
    val arcOne = env.startArc(ChatRecipeOnePlan)
    val arcTwo = env.startArc(ChatRecipeTwoPlan)
    val chatParticleOne = env.getParticle<ChatParticle>(arcOne)
    val chatParticleTwo = env.getParticle<ChatParticle>(arcTwo)
    chatParticleOne.sendMessage("Hello")
    assertThat(waitFor(chatParticleTwo)).isEqualTo("Hello")
    chatParticleTwo.sendMessage("World")
    assertThat(waitFor(chatParticleOne)).isEqualTo("World")
  }
}

class ChatParticle(val receiver: (String, Particle) -> Unit) : AbstractChatParticle() {

  override fun onReady() {
    handles.inMessage.onUpdate { delta ->
      if (delta.new != null) {
        receiver(handles.inMessage.fetch()?.message ?: "", this)
      }
    }
  }

  suspend fun sendMessage(msg: String) {
    withContext(handles.dispatcher) {
      handles.outMessage.store(Chat(msg))
    }
  }
}
