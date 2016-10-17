package bkoruznjak.from.hr.antenazagreb.activity;

import android.media.MediaCodec;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.bluelinelabs.logansquare.LoganSquare;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;

import java.io.IOException;

import bkoruznjak.from.hr.antenazagreb.R;
import bkoruznjak.from.hr.antenazagreb.model.network.PodcastModel;
import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by bkoruznjak on 16/10/2016.
 */

public class SinglePodcastActivity extends AppCompatActivity implements ExoPlayer.Listener, SeekBar.OnSeekBarChangeListener {

    private final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    private final int BUFFER_SEGMENT_COUNT = 256;
    @BindView(R.id.podcastCardTitleTextViewBig)
    TextView podcastTitle;
    @BindView(R.id.podcastCardTopTextViewBig)
    TextView podcastSummary;
    @BindView(R.id.podcastTypeTextView)
    TextView podcastTypeTextView;
    @BindView(R.id.podcastVideoSurface)
    SurfaceView surfaceView;
    @BindView(R.id.podcastSeekBar)
    SeekBar podcastSeekBar;
    //private ExitActivityTransition exitTransition;
    private PodcastModel podcastModel;
    private ExoPlayer videoPlayer;
    private Handler mHandler;
    private int RENDERER_COUNT = 300000;
    private int minBufferMs = 250000;
    private long duration = 1000l;
    private long streamProgress = 0l;
    private int seekProgress = 0;
    private boolean isUpdatingSeekBar = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_podcast);
        overridePendingTransition(R.anim.article_enter_in, R.anim.article_enter_out);
        ButterKnife.bind(this);
        String jsonPodcast = getIntent().getStringExtra("PODCAST");
        try {
            podcastModel = LoganSquare.parse(jsonPodcast, PodcastModel.class);
        } catch (IOException IOex) {

        }
        mHandler = new Handler();
        updateViewContainer(podcastModel);

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void updateViewContainer(PodcastModel podcastModel) {
        if (podcastModel != null) {
            podcastTitle.setText(podcastModel.name);
            podcastSummary.setText("" + podcastModel.summary);
            String url = podcastModel.url;
            String podcastType = url.substring(url.length() - 3, url.length());

            switch (podcastType.toLowerCase()) {
                case "mp3":
                    //todo set audio only icon
                    podcastTypeTextView.setText("audio podcast");
                    break;
                case "mp4":
                    //todo set audio/video icon
                    podcastTypeTextView.setText("video podcast");
                    setupAndStartVideoPlayer(url);
                    break;
                default:
                    break;
            }
        }
    }

    private void setupAndStartVideoPlayer(String url) {
        String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.11; rv:40.0) Gecko/20100101 Firefox/40.0";
        Allocator allocator = new DefaultAllocator(minBufferMs);
        DataSource dataSource = new DefaultUriDataSource(this, null, userAgent);


        ExtractorSampleSource sampleSource = new ExtractorSampleSource(Uri.parse(url), dataSource, allocator,
                BUFFER_SEGMENT_COUNT * BUFFER_SEGMENT_SIZE);

        MediaCodecVideoTrackRenderer videoRenderer = new
                MediaCodecVideoTrackRenderer(this, sampleSource, MediaCodecSelector.DEFAULT,
                MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);

        MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource, MediaCodecSelector.DEFAULT);

        videoPlayer = ExoPlayer.Factory.newInstance(RENDERER_COUNT);
        videoPlayer.addListener(this);
        podcastSeekBar.setOnSeekBarChangeListener(this);
        videoPlayer.prepare(videoRenderer, audioRenderer);
        videoPlayer.sendMessage(videoRenderer,
                MediaCodecVideoTrackRenderer.MSG_SET_SURFACE,
                surfaceView.getHolder().getSurface());
        videoPlayer.setPlayWhenReady(true);
    }

    @Override
    public void onBackPressed() {
        //exitTransition.exit(this)
        cleanUpVideoPlayer();
        finish();
        overridePendingTransition(R.anim.article_exit_in, R.anim.article_exit_out);
    }

    private void cleanUpVideoPlayer() {
        if (videoPlayer != null) {
            isUpdatingSeekBar = false;
            videoPlayer.stop();
            videoPlayer.seekTo(0);
            videoPlayer.removeListener(this);
            videoPlayer.release();
            videoPlayer = null;
            System.gc();
        }
    }


    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        switch (playbackState) {
            case ExoPlayer.STATE_BUFFERING:
                break;
            case ExoPlayer.STATE_ENDED:
                break;
            case ExoPlayer.STATE_IDLE:
                break;
            case ExoPlayer.STATE_PREPARING:
                break;
            case ExoPlayer.STATE_READY:
                duration = videoPlayer.getDuration();
                break;
            default:
                break;
        }
    }

    @Override
    public void onPlayWhenReadyCommitted() {
        isUpdatingSeekBar = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (videoPlayer != null && isUpdatingSeekBar) {
                    int currentPosition = (int) videoPlayer.getCurrentPosition() / 1000;
                    long durationInMillis = videoPlayer.getDuration() == ExoPlayer.UNKNOWN_TIME ? 0
                            : (int) videoPlayer.getDuration();
                    long durationInSeconds = durationInMillis / 1000;
                    if (durationInSeconds != 0) {
                        podcastSeekBar.setProgress((int) (currentPosition * 100 / durationInSeconds));
                    }
                }
                mHandler.postDelayed(this, 1000);
            }
        });

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        isUpdatingSeekBar = false;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (videoPlayer != null && fromUser) {
            seekProgress = progress;
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        streamProgress = seekProgress;
        long duration = videoPlayer.getDuration() == ExoPlayer.UNKNOWN_TIME ? 0
                : (int) videoPlayer.getDuration();
        long seekingSector = duration / 100;
        videoPlayer.seekTo(seekingSector * streamProgress);
    }
}