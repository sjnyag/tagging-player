package com.sjn.taggingplayer.db;

import com.sjn.taggingplayer.constant.RecordType;

import org.joda.time.DateTime;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(prefix = "m")
@AllArgsConstructor(suppressConstructorProperties = true)
@NoArgsConstructor
@Getter
@Setter
public class SongHistory extends RealmObject {

    @PrimaryKey
    public long mId;
    private Song mSong;
    public String mRecordedAt;
    public String mRecordType;
    public Device mDevice;
    public float mLatitude;
    public float mLongitude;
    public float mAccuracy;
    public float mAltitude;

    public void setValues(Song song, RecordType recordType, Device device, DateTime dateTime) {
        setSong(song);
        setRecordedAt(dateTime.toString());
        setRecordType(recordType);
        setDevice(device);
    }

    public void setRecordType(RecordType recordType) {
        this.mRecordType = recordType.getValue();
    }

    public RecordType getRecordType() {
        return RecordType.of(mRecordType);
    }
}