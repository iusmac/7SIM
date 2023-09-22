package com.github.iusmac.sevensim.telephony;

import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.Objects;

import static android.telephony.SubscriptionManager.INVALID_SIM_SLOT_INDEX;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

/**
 * This class is a data transfer object (DTO) representing a SIM subscription.
 */
public final class Subscription implements Parcelable {
    private int mId = INVALID_SUBSCRIPTION_ID;

    private int mSlotIndex = INVALID_SIM_SLOT_INDEX;

    private @SimState int mSimState = SimState.UNKNOWN;

    private @ColorInt int mIconTint = Color.BLACK;

    private String mName = "";

    @IntRange(from = INVALID_SUBSCRIPTION_ID)
    public int getId() {
        return mId;
    }

    public void setId(final @IntRange(from = INVALID_SUBSCRIPTION_ID) int id) {
        mId = id;
    }

    @IntRange(from = INVALID_SIM_SLOT_INDEX)
    public int getSlotIndex() {
        return mSlotIndex;
    }

    public void setSlotIndex(final @IntRange(from = INVALID_SIM_SLOT_INDEX) int slotIndex) {
        mSlotIndex = slotIndex;
    }

    public @SimState int getSimState() {
        return mSimState;
    }

    public void setSimState(final @SimState int simState) {
        mSimState = simState;
    }

    public @NonNull String getSimName() {
        return mName;
    }

    public void setSimName(final @NonNull String simName) {
        mName = simName;
    }

    public @ColorInt int getIconTint() {
        return mIconTint;
    }

    public void setIconTint(final @ColorInt int iconTint) {
        mIconTint = iconTint;
    }

    public boolean isSimEnabled() {
        return mSimState == SimState.ENABLED;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Subscription subToCompare = (Subscription) o;
        return mId == subToCompare.mId
            && mSlotIndex == subToCompare.mSlotIndex
            && mSimState == subToCompare.mSimState
            && mIconTint == subToCompare.mIconTint
            && mName.equals(subToCompare.mName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mSlotIndex, mSimState, mIconTint, mName);
    }

    @Override
    public String toString() {
        return "Subscription {"
            + " id=" + mId
            + " slotIndex=" + mSlotIndex
            + " simState=" + TelephonyUtils.simStateToString(mSimState)
            + " iconTint=" + mIconTint
            + " name=" + mName
            + " }";
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeInt(mId);
        dest.writeInt(mSlotIndex);
        dest.writeInt(mSimState);
        dest.writeInt(mIconTint);
        dest.writeString(mName);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final @NonNull Creator<Subscription> CREATOR = new Creator<Subscription>() {
        @Override
        public Subscription createFromParcel(final Parcel in) {
            final Subscription sub = new Subscription();
            sub.setId(in.readInt());
            sub.setSlotIndex(in.readInt());
            sub.setSimState(in.readInt());
            sub.setIconTint(in.readInt());
            sub.setSimName(in.readString());
            return sub;
        }

        @Override
        public Subscription[] newArray(final int size) {
            return new Subscription[size];
        }
    };
}
