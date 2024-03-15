package com.github.iusmac.sevensim.telephony;

import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

import static android.telephony.SubscriptionManager.INVALID_SIM_SLOT_INDEX;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

/**
 * <p>This class is a data transfer object (DTO) representing a SIM subscription.
 *
 * <p>This class is supposed to be used with a persisted database context, that will instantiate
 * this DTO and fill all non-{@link Ignore} annotated fields. Clients are responsible for populating
 * all additional fields as needed.
 */
@Entity(
    tableName = "subscriptions"
)
public final class Subscription implements Parcelable {
    @PrimaryKey
    @ColumnInfo(name = "id")
    private int mId = INVALID_SUBSCRIPTION_ID;

    @Ignore
    private int mSlotIndex = INVALID_SIM_SLOT_INDEX;

    @Ignore
    private @SimState int mSimState = SimState.UNKNOWN;

    @Ignore
    private @ColorInt int mIconTint = Color.BLACK;

    @Ignore
    private String mName = "";

    @ColumnInfo(name = "lastActivatedTime")
    private LocalDateTime mLastActivatedTime = LocalDateTime.MIN;

    @ColumnInfo(name = "lastDeactivatedTime")
    private LocalDateTime mLastDeactivatedTime = LocalDateTime.MIN;

    @ColumnInfo(name = "keepDisabledAcrossBoots")
    private Boolean mKeepDisabledAcrossBoots;

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

    public LocalDateTime getLastActivatedTime() {
        return mLastActivatedTime;
    }

    public void setLastActivatedTime(final LocalDateTime lastActivatedTime) {
        mLastActivatedTime = lastActivatedTime;
    }

    public LocalDateTime getLastDeactivatedTime() {
        return mLastDeactivatedTime;
    }

    public void setLastDeactivatedTime(final LocalDateTime lastDeactivatedTime) {
        mLastDeactivatedTime = lastDeactivatedTime;
    }

    public Boolean getKeepDisabledAcrossBoots() {
        return mKeepDisabledAcrossBoots;
    }

    public void keepDisabledAcrossBoots(final Boolean keepDisabledAcrossBoots) {
        mKeepDisabledAcrossBoots = keepDisabledAcrossBoots;
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
            && mName.equals(subToCompare.mName)
            && mLastActivatedTime.equals(subToCompare.mLastActivatedTime)
            && mLastDeactivatedTime.equals(subToCompare.mLastDeactivatedTime)
            && mKeepDisabledAcrossBoots == subToCompare.mKeepDisabledAcrossBoots;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mSlotIndex, mSimState, mIconTint, mName, mLastActivatedTime,
                mLastDeactivatedTime, mKeepDisabledAcrossBoots);
    }

    @Override
    public String toString() {
        return "Subscription {"
            + " id=" + mId
            + " slotIndex=" + mSlotIndex
            + " simState=" + TelephonyUtils.simStateToString(mSimState)
            + " iconTint=" + mIconTint
            + " name=" + mName
            + " lastActivatedTime=" + mLastActivatedTime
            + " lastDeactivatedTime=" + mLastDeactivatedTime
            + " keepDisabledAcrossBoots=" + mKeepDisabledAcrossBoots
            + " }";
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeInt(mId);
        dest.writeInt(mSlotIndex);
        dest.writeInt(mSimState);
        dest.writeInt(mIconTint);
        dest.writeString(mName);
        dest.writeString(mLastActivatedTime.toString());
        dest.writeString(mLastDeactivatedTime.toString());
        dest.writeString(mKeepDisabledAcrossBoots != null ?
                mKeepDisabledAcrossBoots.toString() : null);
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
            try {
                final String lastActivatedTime = in.readString();
                if (lastActivatedTime != null) {
                    sub.setLastActivatedTime(LocalDateTime.parse(lastActivatedTime));
                }
            } catch (DateTimeParseException ignored) {}

            try {
                final String lastDeactivatedTime = in.readString();
                if (lastDeactivatedTime != null) {
                    sub.setLastDeactivatedTime(LocalDateTime.parse(lastDeactivatedTime));
                }
            } catch (DateTimeParseException ignored) {}

            Optional.ofNullable(in.readString()).ifPresent((v) ->
                    sub.keepDisabledAcrossBoots(Boolean.parseBoolean(v)));

            return sub;
        }

        @Override
        public Subscription[] newArray(final int size) {
            return new Subscription[size];
        }
    };
}
