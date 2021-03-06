package bkoruznjak.from.hr.antenazagreb.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.net.Uri;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.squareup.otto.Subscribe;

import bkoruznjak.from.hr.antenazagreb.R;
import bkoruznjak.from.hr.antenazagreb.RadioApplication;
import bkoruznjak.from.hr.antenazagreb.activity.MainActivity;
import bkoruznjak.from.hr.antenazagreb.bus.RadioBus;
import bkoruznjak.from.hr.antenazagreb.constants.NetworkConstants;
import bkoruznjak.from.hr.antenazagreb.enums.RadioCommandEnum;
import bkoruznjak.from.hr.antenazagreb.enums.RadioStateEnum;
import bkoruznjak.from.hr.antenazagreb.metadata.IcyMetadataHandler;
import bkoruznjak.from.hr.antenazagreb.model.bus.RadioStateModel;
import okhttp3.OkHttpClient;

/**
 * Created by bkoruznjak on 29/06/16.
 */
public class RadioService extends Service implements ExoPlayer.Listener, MediaCodecTrackRenderer.EventListener {

    private RadioBus myBus;
    private RadioStateModel radioState;
    private ExoPlayer mExoPlayer;
    private IcyMetadataHandler icyMetadataHandler;
    private MediaCodecAudioTrackRenderer mAudioTrackRenderer;
    private int mNotificationIcon;
    private boolean isProblemEncountered;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        myBus = ((RadioApplication) getApplication()).getBus();
        RadioApplication antenaApp = ((RadioApplication) getApplication());
        radioState = antenaApp.getRadioStateModel();
        mNotificationIcon = antenaApp.getRadioNotificationIcon();
        radioState.setServiceUp(true);
        if (mExoPlayer == null) {
            mExoPlayer = ExoPlayer.Factory.newInstance(1);
            mExoPlayer.addListener(this);
        }
        myBus.register(this);
        icyMetadataHandler = new IcyMetadataHandler(10000, radioState, myBus);
        icyMetadataHandler.fetchMetaData();
        Log.d("BBB", "service CREATED");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("BBB", "service STARTED");
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        Notification notification = new Notification.Builder(this)
                .setContentTitle("Antena Radio")
                .setContentText(radioState.getSongAuthor().concat(" - ").concat(radioState.getSongTitle()))
                .setSmallIcon(mNotificationIcon)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), mNotificationIcon))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        notification.flags |= Notification.FLAG_NO_CLEAR;
        startForeground(1337, notification);
        Log.d("bbb", "starting service for:" + radioState.getDefaultStream() + " on url:" + radioState.getStreamUri());
        prepareRadioStream(radioState.getStreamUri(), true);
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        radioState.setStateEnum(RadioStateEnum.ENDED);
        radioState.setServiceUp(false);
        myBus.unregister(this);
        purgeRadio();
        Log.d("BBB", "service DESTROYED");
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (playbackState == ExoPlayer.STATE_ENDED) {
            radioState.setStateEnum(RadioStateEnum.ENDED);
            myBus.post(RadioStateEnum.ENDED);
        }
        switch (playbackState) {
            case ExoPlayer.STATE_BUFFERING:
                Log.d("BBB", "STATE BUFFERING IN RADIO SERVICE");
                radioState.setStateEnum(RadioStateEnum.BUFFERING);
                myBus.post(RadioStateEnum.BUFFERING);
                break;
            case ExoPlayer.STATE_ENDED:
                Log.d("BBB", "STATE ENDED IN RADIO SERVICE");
                radioState.setStateEnum(RadioStateEnum.ENDED);
                myBus.post(RadioStateEnum.ENDED);
                break;
            case ExoPlayer.STATE_IDLE:
                Log.d("BBB", "STATE IDLE IN RADIO SERVICE");
                radioState.setStreamInterrupted(true);
                radioState.setStateEnum(RadioStateEnum.IDLE);
                myBus.post(RadioStateEnum.IDLE);
                break;
            case ExoPlayer.STATE_PREPARING:
                Log.d("BBB", "STATE PREPARING IN RADIO SERVICE");
                radioState.setStateEnum(RadioStateEnum.PREPARING);
                myBus.post(RadioStateEnum.PREPARING);
                break;
            case ExoPlayer.STATE_READY:
                Log.d("BBB", "STATE READY IN RADIO SERVICE");
                radioState.setStreamInterrupted(false);
                radioState.setStateEnum(RadioStateEnum.READY);
                myBus.post(RadioStateEnum.READY);
                break;
            default:
                Log.d("BBB", "STATE UNKNOWN IN RADIO SERVICE");
                radioState.setStateEnum(RadioStateEnum.UNKNOWN);
                myBus.post(RadioStateEnum.UNKNOWN);
                break;
        }
    }

    @Override
    public void onPlayWhenReadyCommitted() {
        Log.d("BBB", "ON PLAY WHEN READY COMMITED");

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        isProblemEncountered = true;
        int duration = Toast.LENGTH_SHORT;
        Crashlytics.log(Log.ERROR, "AntenaZagreb", "Exo Error:" + error);
        Toast toast = Toast.makeText(this, getResources().getString(R.string.error_radio), duration);
        toast.show();
        stop();
        radioState.setStateEnum(RadioStateEnum.ENDED);
        radioState.setMusicPlaying(false);
        myBus.post(RadioStateEnum.ENDED);
        Log.e("BBB", "on player error," + error.toString());
    }

    private void prepareRadioStream(String streamURI, boolean playWhenReady) {
        if (radioState.isMusicPlaying() && mExoPlayer != null) {
            mExoPlayer.stop();
            radioState.setMusicPlaying(false);
        }
        Allocator bufferAllocator = new DefaultAllocator(NetworkConstants.BUFFER_SEGMENT_SIZE);
        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        OkHttpClient okHttpClient = new OkHttpClient();
        OkHttpDataSource okHttpDataSource = new OkHttpDataSource(okHttpClient, streamURI, null, bandwidthMeter);
        DataSource okDataSource = new DefaultUriDataSource(this, bandwidthMeter, okHttpDataSource);
        ExtractorSampleSource extractorSampleSource = new ExtractorSampleSource(Uri.parse(streamURI), okDataSource, bufferAllocator, NetworkConstants.BUFFER_SEGMENT_COUNT_256 * NetworkConstants.BUFFER_SEGMENT_SIZE, 3);
        mAudioTrackRenderer = new MediaCodecAudioTrackRenderer(extractorSampleSource, MediaCodecSelector.DEFAULT);
        mExoPlayer.prepare(mAudioTrackRenderer);

        if (playWhenReady) {
            play();
        }
    }

    private void play() {
        if (mExoPlayer != null) {
            radioState.setMusicPlaying(true);
            mExoPlayer.setPlayWhenReady(true);
            AudioManager audioManager =
                    (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, RadioApplication.getInstance().getRadioVolume(), AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
        }
    }

    private void pause() {
        if (mExoPlayer != null) {
            radioState.setMusicPlaying(false);
            mExoPlayer.setPlayWhenReady(false);
            radioState.setStateEnum(RadioStateEnum.ENDED);
        }
    }

    private void stop() {
        if (mExoPlayer != null) {
            pause();
            mExoPlayer.stop();
            mExoPlayer.seekTo(0);
        }
    }

    public void purgeRadio() {
        stop();
        if (mExoPlayer != null) {
            mExoPlayer.removeListener(this);
            mExoPlayer.release();
            mExoPlayer = null;
//            System.gc();
        }
    }

    @Subscribe
    public void radioControlHandler(RadioCommandEnum command) {
        switch (command) {
            case PLAY:
                if (isProblemEncountered) {
                    if (radioState.isMusicPlaying()) {
                        prepareRadioStream(radioState.getStreamUri(), true);
                    } else {
                        prepareRadioStream(radioState.getStreamUri(), false);
                    }
                    isProblemEncountered = false;
                }
                play();
                break;
            case PAUSE:
                pause();
                break;
            case STOP:
                stop();
                break;
            default:
                break;
        }
    }

    @Subscribe
    public void changeStation(String stationURI) {
        if (stationURI.toLowerCase().startsWith(NetworkConstants.STREAM_PREFIX_STRING)) {
            radioState.setStreamUri(stationURI);
            if (radioState.isMusicPlaying()) {
                prepareRadioStream(stationURI, true);
            } else {
                prepareRadioStream(stationURI, false);
            }
        }
    }

    @Override
    public void onDecoderInitializationError(MediaCodecTrackRenderer.DecoderInitializationException e) {

    }

    @Override
    public void onCryptoError(MediaCodec.CryptoException e) {

    }

    @Override
    public void onDecoderInitialized(String decoderName, long elapsedRealtimeMs, long initializationDurationMs) {

    }
}
