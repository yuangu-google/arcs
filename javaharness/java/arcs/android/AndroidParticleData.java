package arcs.android;

import android.os.Parcelable;

import com.google.auto.value.AutoValue;

import arcs.api.Particle;

@AutoValue
public abstract class AndroidParticleData implements Parcelable {

  abstract String getId();
  abstract String getName();
  abstract String getProvideSlotId();

  public static AndroidParticleData create(Particle particle, String provideSlotId) {
    return new AutoValue_AndroidParticleData(
        particle.getId(), particle.getName(), provideSlotId);
  }
}
