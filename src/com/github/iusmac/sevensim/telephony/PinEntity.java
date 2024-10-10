package com.github.iusmac.sevensim.telephony;

import java.util.Arrays;
import java.util.Objects;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "pin_storage",
    indices = {
        @Index(name = "idx_subId", value = {"sub_id"}, unique = true)
    }
)
public final class PinEntity {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long mId;

    @ColumnInfo(name = "sub_id")
    private int mSubscriptionId;

    @NonNull
    @ColumnInfo(name = "data")
    private byte[] mData;

    @NonNull
    @ColumnInfo(name = "iv")
    private byte[] mIV;

    @ColumnInfo(name = "invalid")
    private boolean mInvalid;

    @ColumnInfo(name = "corrupted")
    private boolean mCorrupted;

    @Ignore
    private String mClearPin;

    long getId() {
        return mId;
    }

    void setId(final long id) {
        mId = id;
    }

    public int getSubscriptionId() {
        return mSubscriptionId;
    }

    public void setSubscriptionId(final int subId) {
        mSubscriptionId = subId;
    }

    public @Nullable String getClearPin() {
        return mClearPin;
    }

    public void setClearPin(final @NonNull String pin) {
        mClearPin = pin;
        mData = null;
        mIV = null;
        mCorrupted = false;
    }

    byte[] getData() {
        return mData;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public void setData(final byte[] data) {
        mData = data;
    }

    byte[] getIV() {
        return mIV;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public void setIV(final byte[] iv) {
        mIV = iv;
    }

    public boolean isCorrupted() {
        return mCorrupted;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public void setCorrupted(final boolean corrupted) {
        mCorrupted = corrupted;
    }

    public boolean isInvalid() {
        return mInvalid;
    }

    public void setInvalid(final boolean invalid) {
        mInvalid = invalid;
    }

    public boolean isEncrypted() {
        return mData != null && mIV != null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mSubscriptionId, Arrays.hashCode(mData), Arrays.hashCode(mIV),
                mInvalid, mCorrupted, mClearPin);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final PinEntity other = (PinEntity) o;
        return mId == other.mId
            && mSubscriptionId == other.mSubscriptionId
            && Arrays.equals(mData, other.mData)
            && Arrays.equals(mIV, other.mIV)
            && mInvalid == other.mInvalid
            && mCorrupted == other.mCorrupted
            && TextUtils.equals(mClearPin, other.mClearPin);
    }

    @Override
    public String toString() {
        return "PinEntity {"
            + " id=" + mId
            + " subscriptionId=" + mSubscriptionId
            + " data=[{ " + (mData != null ? "has" : "empty") + " data }]"
            + " IV=[{ " + (mIV != null ? "has" : "empty") + " data }]"
            + " clearPin.isEmpty=" + TextUtils.isEmpty(mClearPin)
            + " corrupted=" + mCorrupted
            + " invalid=" + mInvalid
            + " }";
    }
}
