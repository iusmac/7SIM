package com.github.iusmac.sevensim.telephony;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
    }

    byte[] getData() {
        return mData;
    }

    void setData(final byte[] data) {
        mData = data;
    }

    byte[] getIV() {
        return mIV;
    }

    void setIV(final byte[] iv) {
        mIV = iv;
    }

    public boolean isEncrypted() {
        return mData != null && mIV != null;
    }

    @Override
    public String toString() {
        return "PinEntity {"
            + " id=" + mId
            + " subscriptionId=" + mSubscriptionId
            + " data=[{ " + (mData != null ? "has" : "empty") + " data }]"
            + " IV=[{ " + (mIV != null ? "has" : "empty") + " data }]"
            + " clearPin.isEmpty=" + TextUtils.isEmpty(mClearPin)
            + " }";
    }
}
