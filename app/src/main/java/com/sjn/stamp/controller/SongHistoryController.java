package com.sjn.stamp.controller;

import android.content.Context;
import android.os.AsyncTask;
import android.support.v4.media.MediaMetadataCompat;

import com.sjn.stamp.constant.RecordType;
import com.sjn.stamp.db.Artist;
import com.sjn.stamp.db.Device;
import com.sjn.stamp.db.RankedArtist;
import com.sjn.stamp.db.RankedSong;
import com.sjn.stamp.db.Song;
import com.sjn.stamp.db.SongHistory;
import com.sjn.stamp.db.TotalSongHistory;
import com.sjn.stamp.db.dao.DeviceDao;
import com.sjn.stamp.db.dao.SongDao;
import com.sjn.stamp.db.dao.SongHistoryDao;
import com.sjn.stamp.db.dao.TotalSongHistoryDao;
import com.sjn.stamp.ui.custom.PeriodSelectLayout;
import com.sjn.stamp.utils.LogHelper;
import com.sjn.stamp.utils.MediaItemHelper;
import com.sjn.stamp.utils.NotificationHelper;
import com.sjn.stamp.utils.RealmHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.realm.Realm;

public class SongHistoryController {

    private static final String TAG = LogHelper.makeLogTag(SongHistoryController.class);

    private Context mContext;
    private DeviceDao mDeviceDao;
    private SongDao mSongDao;
    private SongHistoryDao mSongHistoryDao;
    private TotalSongHistoryDao mTotalSongHistoryDao;

    public SongHistoryController(Context context) {
        mContext = context;
        mDeviceDao = DeviceDao.getInstance();
        mSongDao = SongDao.getInstance();
        mSongHistoryDao = SongHistoryDao.getInstance();
        mTotalSongHistoryDao = TotalSongHistoryDao.getInstance();
    }

    public void deleteSongHistory(long songHistoryId) {
        Realm realm = RealmHelper.getRealmInstance();
        mSongHistoryDao.remove(realm, songHistoryId);
        realm.close();
    }

    public void onPlay(MediaMetadataCompat track, Date date) {
        LogHelper.d(TAG, "insertPLAY ", track.getDescription().getTitle());
        registerHistory(track, RecordType.PLAY, date);
    }

    public void onSkip(MediaMetadataCompat track, Date date) {
        LogHelper.d(TAG, "insertSKIP ", track.getDescription().getTitle());
        registerHistory(track, RecordType.SKIP, date);
    }

    public void onStart(MediaMetadataCompat track, Date date) {
        LogHelper.d(TAG, "insertSTART ", track.getDescription().getTitle());
        registerHistory(track, RecordType.START, date);
    }

    public void onComplete(MediaMetadataCompat track, Date date) {
        LogHelper.d(TAG, "insertComplete ", track.getDescription().getTitle());
        registerHistory(track, RecordType.COMPLETE, date);
    }

    private void registerHistory(MediaMetadataCompat track, RecordType recordType, Date date) {
        Song song = createSong(track);
        Realm realm = RealmHelper.getRealmInstance();
        int playCount = mTotalSongHistoryDao.saveOrIncrement(realm, createTotalSongHistory(song, recordType));
        mSongHistoryDao.save(realm, createSongHistory(song, createDevice(), recordType, date, playCount));
        if (recordType == RecordType.PLAY) {
            sendNotificationBySongCount(realm, song, playCount);
            sendNotificationByArtistCount(song);
        }
        realm.close();
    }

    private void sendNotificationBySongCount(Realm realm, Song song, int playCount) {
        if (NotificationHelper.isSendPlayedNotification(playCount)) {
            SongHistory oldestSongHistory = mSongHistoryDao.findOldest(realm, song);
            NotificationHelper.sendPlayedNotification(mContext, song.getTitle(), song.getAlbumArtUri(), playCount, oldestSongHistory.getRecordedAt());
        }
    }

    private void sendNotificationByArtistCount(Song song) {
        new ArtistCountAsyncTask(song.getArtist().getName()).execute();
    }

    private TotalSongHistory createTotalSongHistory(Song song, RecordType recordType) {
        TotalSongHistory totalSongHistory = mTotalSongHistoryDao.newStandalone();
        totalSongHistory.parseSongQueue(song, recordType);
        return totalSongHistory;
    }

    private Device createDevice() {
        Device device = mDeviceDao.newStandalone();
        device.configure();
        return device;
    }

    private Song createSong(MediaMetadataCompat track) {
        Song song = mSongDao.newStandalone();
        MediaItemHelper.updateSong(song, track);
        return song;
    }

    private SongHistory createSongHistory(Song song, Device device, RecordType recordType, Date date, int count) {
        SongHistory songHistory = mSongHistoryDao.newStandalone();
        songHistory.applyValues(song, recordType, device, date, count);
        return songHistory;
    }

    public List<MediaMetadataCompat> getTopSongList() {
        Realm realm = RealmHelper.getRealmInstance();
        List<MediaMetadataCompat> trackList = new ArrayList<>();
        List<TotalSongHistory> historyList = mTotalSongHistoryDao.getOrderedList(realm);
        for (TotalSongHistory totalSongHistory : historyList) {
            if (totalSongHistory.getPlayCount() == 0 || trackList.size() > new UserSettingController(mContext).getMostPlayedSongSize()) {
                break;
            }
            trackList.add(MediaItemHelper.convertToMetadata(totalSongHistory.getSong()));
        }
        realm.close();
        return trackList;
    }

    public List<SongHistory> getManagedTimeline(Realm realm) {
        return mSongHistoryDao.timeline(realm, RecordType.PLAY.getValue());
    }

    public List<RankedSong> getRankedSongList(Realm realm, PeriodSelectLayout.Period period) {
        Date from = period.from() == null ? null : period.from().toDateTimeAtStartOfDay().toDate();
        Date to = period.to() == null ? null : period.to().toDateTimeAtStartOfDay().plusDays(1).toDate();
        if (period.getPeriodType() == PeriodSelectLayout.PeriodType.TOTAL) {
            from = null;
            to = null;
        }
        return getRankedSongList(realm, from, to);
    }

    public List<RankedArtist> getRankedArtistList(Realm realm, PeriodSelectLayout.Period period) {
        Date from = period.from() == null ? null : period.from().toDateTimeAtStartOfDay().toDate();
        Date to = period.to() == null ? null : period.to().toDateTimeAtStartOfDay().plusDays(1).toDate();
        if (period.getPeriodType() == PeriodSelectLayout.PeriodType.TOTAL) {
            from = null;
            to = null;
        }
        return getRankedArtistList(realm, from, to);
    }

    private List<RankedSong> getRankedSongList(Realm realm, Date from, Date to) {
        LogHelper.d(TAG, "getRankedSongList start");
        LogHelper.d(TAG, "calc historyList");
        List<SongHistory> historyList = mSongHistoryDao.where(realm, from, to, RecordType.PLAY.getValue());
        Map<Song, Integer> songCountMap = new HashMap<>();
        LogHelper.d(TAG, "put songCountMap");
        for (SongHistory songHistory : historyList) {
            if (songCountMap.containsKey(songHistory.getSong())) {
                songCountMap.put(songHistory.getSong(), songCountMap.get(songHistory.getSong()) + 1);
            } else {
                songCountMap.put(songHistory.getSong(), 1);
            }
        }
        LogHelper.d(TAG, "create rankedSongList");
        List<RankedSong> rankedSongList = new ArrayList<>();
        for (Map.Entry<Song, Integer> entry : songCountMap.entrySet()) {
            rankedSongList.add(new RankedSong(entry.getValue(), entry.getKey()));
        }
        LogHelper.d(TAG, "sort rankedSongList");
        Collections.sort(rankedSongList, new Comparator<RankedSong>() {
            @Override
            public int compare(RankedSong t1, RankedSong t2) {
                return t2.getPlayCount() - t1.getPlayCount();
            }
        });
        if (rankedSongList.size() > 30) {
            rankedSongList = rankedSongList.subList(0, 30);
        }
        LogHelper.d(TAG, "getRankedSongList end");
        return rankedSongList;
    }

    private List<RankedArtist> getRankedArtistList(Realm realm, Date from, Date to) {
        LogHelper.d(TAG, "getRankedArtistList start");
        List<SongHistory> historyList = mSongHistoryDao.where(realm, from, to, RecordType.PLAY.getValue());
        Map<Artist, ArtistCounter> artistMap = new HashMap<>();
        for (SongHistory songHistory : historyList) {
            Artist artist = songHistory.getSong().getArtist();
            ArtistCounter.count(artistMap, artist, songHistory.getSong());
        }
        List<RankedArtist> rankedArtistList = new ArrayList<>();
        for (Map.Entry<Artist, ArtistCounter> e : artistMap.entrySet()) {
            rankedArtistList.add(new RankedArtist(e.getValue().mCount, e.getKey(), e.getValue().mSongCountMap));
        }
        Collections.sort(rankedArtistList, new Comparator<RankedArtist>() {
            @Override
            public int compare(RankedArtist t1, RankedArtist t2) {
                return t2.getPlayCount() - t1.getPlayCount();
            }
        });
        if (rankedArtistList.size() > 30) {
            rankedArtistList = rankedArtistList.subList(0, 30);
        }
        LogHelper.d(TAG, "getRankedArtistList end");
        return rankedArtistList;
    }

    private static class ArtistCounter {
        int mCount = 0;
        Map<Song, Integer> mSongCountMap = new HashMap<>();

        void increment(Song song) {
            mCount++;
            if (mSongCountMap.containsKey(song)) {
                mSongCountMap.put(song, mSongCountMap.get(song) + 1);
            } else {
                mSongCountMap.put(song, 1);
            }
        }

        public static void count(Map<Artist, ArtistCounter> artistMap, Artist artist, Song song) {
            ArtistCounter counter = artistMap.containsKey(artist) ? artistMap.get(artist) : new ArtistCounter();
            counter.increment(song);
            artistMap.put(artist, counter);

        }
    }

    private class ArtistCountAsyncTask extends AsyncTask<Void, Void, Void> {
        String mArtistName;

        ArtistCountAsyncTask(String artistName) {
            mArtistName = artistName;
        }

        @Override
        protected Void doInBackground(Void... params) {
            Realm realm = null;
            try {
                realm = RealmHelper.getRealmInstance();

                List<SongHistory> historyList = mSongHistoryDao.findPlayRecordByArtist(realm, mArtistName);
                int playCount = historyList.size();
                if (NotificationHelper.isSendPlayedNotification(playCount)) {
                    SongHistory oldestSongHistory = mSongHistoryDao.findOldestByArtist(realm, mArtistName);
                    NotificationHelper.sendPlayedNotification(
                            mContext,
                            mArtistName,
                            oldestSongHistory.getSong().getAlbumArtUri(),
                            playCount,
                            oldestSongHistory.getRecordedAt()
                    );
                }
            } finally {
                if (realm != null) {
                    realm.close();
                }
            }
            return null;
        }
    }

}
