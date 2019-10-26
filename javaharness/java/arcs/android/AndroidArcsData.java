package arcs.android;

import android.os.Parcelable;

import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class AndroidArcsData implements Parcelable {

  abstract String getArcId();
  abstract String getPecId();
  abstract String getRecipe();
  abstract String getSessionId();
  abstract List<AndroidParticleData> getParticleList();

  static Builder builder() {
    return new AutoValue_AndroidArcsData.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setArcId(String arcId);
    abstract Builder setPecId(String pecId);
    abstract Builder setRecipe(String recipe);
    abstract Builder setSessionId(String sessionId);
    abstract Builder setParticleList(List<AndroidParticleData> particleList);
    abstract AndroidArcsData build();
  }

}
