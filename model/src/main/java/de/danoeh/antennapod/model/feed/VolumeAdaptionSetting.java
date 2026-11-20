package de.danoeh.antennapod.model.feed;

import android.media.audiofx.AudioEffect;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * A show's volume setting. OFF (0, the stored default) means "usual": the show follows the default in Tune
 * (see {@link #orUsual}). NO_CHANGE means plain volume for this show, whatever Tune says.
 */
public enum VolumeAdaptionSetting {
    OFF(0, 1.0f),
    LIGHT_REDUCTION(1, 0.5f),
    HEAVY_REDUCTION(2, 0.2f),
    LIGHT_BOOST(3, 1.5f),
    MEDIUM_BOOST(4, 2f),
    HEAVY_BOOST(5, 2.5f),
    NO_CHANGE(6, 1.0f);

    private final int value;
    private float adaptionFactor;

    VolumeAdaptionSetting(int value, float adaptionFactor) {
        this.value = value;
        this.adaptionFactor = adaptionFactor;
    }

    public static VolumeAdaptionSetting fromInteger(int value) {
        for (VolumeAdaptionSetting setting : values()) {
            if (setting.value == value) {
                return setting;
            }
        }
        throw new IllegalArgumentException("Cannot map value to VolumeAdaptionSetting: " + value);
    }

    public int toInteger() {
        return value;
    }

    public float getAdaptionFactor() {
        return adaptionFactor;
    }

    /** The setting that plays: this one, or the usual one from Tune when this show is left on usual. */
    public VolumeAdaptionSetting orUsual(@Nullable VolumeAdaptionSetting usual) {
        if (this != OFF) {
            return this;
        }
        return usual == null ? OFF : usual;
    }

    public boolean isBoost() {
        return adaptionFactor > 1.0f;
    }

    @Nullable
    private static Boolean boostSupported = null;

    public static boolean isBoostSupported() {
        if (boostSupported != null) {
            return boostSupported;
        }
        final AudioEffect.Descriptor[] audioEffects = AudioEffect.queryEffects();
        if (audioEffects != null) {
            for (AudioEffect.Descriptor effect : audioEffects) {
                if (effect.type.equals(AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER)) {
                    boostSupported = true;
                    return boostSupported;
                }
            }
        }
        boostSupported = false;
        return boostSupported;
    }

    @VisibleForTesting
    public static void setBoostSupported(@Nullable Boolean boostSupported) {
        VolumeAdaptionSetting.boostSupported = boostSupported;
    }
}
