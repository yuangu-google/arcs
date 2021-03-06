package arcs.android;

import android.content.Context;
import android.os.RemoteException;

import javax.inject.Inject;
import javax.inject.Singleton;

import arcs.api.ArcData;
import arcs.api.ArcsMessageSender;
import arcs.api.Constants;
import arcs.api.PecInnerPort;
import arcs.api.PecInnerPortProxy;
import arcs.api.PecPortManager;
import arcs.api.PortableJson;
import arcs.api.PortableJsonParser;
import arcs.api.UiBroker;

@Singleton
class ArcsShellApi {

  @Inject
  PortableJsonParser jsonParser;

  @Inject
  AndroidArcsEnvironment environment;

  @Inject
  ArcsMessageSender arcsMessageSender;

  @Inject
  PecPortManager pecPortManager;

  @Inject
  UiBroker uiBroker;

  private boolean arcsReady;

  @Inject
  ArcsShellApi() {}

  void init(Context context) {
    arcsReady = false;
    environment.addReadyListener(recipes -> arcsReady = true);
    environment.init(context);
  }

  void destroy() {
    environment.destroy();
  }

  void startArc(ArcData arcData, IRemotePecCallback callback) {
    PecInnerPortProxy pecInnerPortProxy =
        new PecInnerPortProxy(
            message -> {
              try {
                callback.onMessage(message);
              } catch (RemoteException e) {
                throw new RuntimeException(e);
              }
            },
            jsonParser);
    pecPortManager.addPecInnerPortProxy(arcData.getPecId(), pecInnerPortProxy);

    runWhenReady(() -> {
      PecInnerPort pecInnerPort = null;
      for (ArcData.ParticleData particleData : arcData.getParticleList()) {
        if (particleData.getParticle() != null) {
          if (pecInnerPort == null) {
            pecInnerPort = pecPortManager.getOrCreatePecInnerPort(
                arcData.getPecId(), arcData.getSessionId());
          }
          pecInnerPort.mapParticle(particleData.getParticle());
        }
      }

      arcsMessageSender.sendMessageToArcs(constructRunArcRequest(arcData));
    });
  }

  void stopArc(ArcData arcData) {
    runWhenReady(() -> arcsMessageSender.sendMessageToArcs(
        constructStopArcRequest(arcData)));
    pecPortManager.removePecPort(arcData.getPecId());
  }

  void registerRemoteRenderer(String modality, IRemoteOutputCallback callback) {
    uiBroker.registerRenderer(
        modality,
        content -> {
          try {
            callback.onOutput(jsonParser.stringify(content));
          } catch (RemoteException e) {
            throw new RuntimeException(e);
          }
          return true;
        });
  }

  void sendMessageToArcs(String message) {
    runWhenReady(() -> arcsMessageSender.sendMessageToArcs(message));
  }

  private String constructRunArcRequest(ArcData arcData) {
    PortableJson request = jsonParser
        .emptyObject()
        .put(Constants.MESSAGE_FIELD, Constants.RUN_ARC_MESSAGE)
        .put(Constants.ARC_ID_FIELD, arcData.getArcId())
        .put(Constants.PEC_ID_FIELD, arcData.getPecId())
        .put(Constants.RECIPE_FIELD, arcData.getRecipe());
    PortableJson particles = jsonParser.emptyArray();
    arcData.getParticleList().forEach(particleData -> {
      if (particleData.getName() != null && particleData.getId() != null) {
        PortableJson particleJson =
            jsonParser
                .emptyObject()
                .put(Constants.PARTICLE_ID_FIELD, particleData.getId())
                .put(Constants.PARTICLE_NAME_FIELD, particleData.getName());
        if (particleData.getProvidedSlotId() != null) {
          particleJson.put(Constants.PROVIDED_SLOT_ID_FIELD, particleData.getProvidedSlotId());
        }
        particles.put(0, particleJson);
      }
    });

    if (particles.getLength() > 0) {
      request.put(Constants.PARTICLES_FIELD, particles);
    }
    return jsonParser.stringify(request);
  }

  private String constructStopArcRequest(ArcData arcData) {
    return jsonParser.stringify(
        jsonParser
            .emptyObject()
            .put(Constants.MESSAGE_FIELD, Constants.STOP_ARC_MESSAGE)
            .put(Constants.ARC_ID_FIELD, arcData.getArcId())
            .put(Constants.PEC_ID_FIELD, arcData.getPecId()));
  }


  private void runWhenReady(Runnable runnable) {
    if (arcsReady) {
      runnable.run();
    } else {
      environment.addReadyListener(recipes -> runnable.run());
    }
  }
}
