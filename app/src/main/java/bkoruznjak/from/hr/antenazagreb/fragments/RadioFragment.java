package bkoruznjak.from.hr.antenazagreb.fragments;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import bkoruznjak.from.hr.antenazagreb.R;
import bkoruznjak.from.hr.antenazagreb.RadioApplication;
import bkoruznjak.from.hr.antenazagreb.activity.MainActivity;
import bkoruznjak.from.hr.antenazagreb.bus.RadioBus;
import bkoruznjak.from.hr.antenazagreb.enums.RadioStateEnum;
import bkoruznjak.from.hr.antenazagreb.model.bus.RadioStateModel;
import bkoruznjak.from.hr.antenazagreb.model.db.SongModel;
import bkoruznjak.from.hr.antenazagreb.views.RippleBackground;
import bkoruznjak.from.hr.antenazagreb.views.VolumeSlider;
import butterknife.BindView;
import butterknife.ButterKnife;

public class RadioFragment extends Fragment implements VolumeSlider.OnSectorChangedListener {

    @BindView(R.id.volumeControl)
    VolumeSlider volumeControl;
    @BindView(R.id.radioStateTextView)
    TextView txtRadioState;
    @BindView(R.id.songInfoTextView)
    TextView txtSongInfo;
    View radioFragmentView;
    private RadioBus myBus;
    private RadioStateModel mRadioStateModel;
    private Animation infiniteRotateAnim;
    private RippleBackground rippleBackground;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        radioFragmentView = inflater.inflate(R.layout.fragment_radio, container, false);
        init(radioFragmentView);
        return radioFragmentView;
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (rippleBackground != null) {
            rippleBackground.hardStopRippleAnimation();
            rippleBackground = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        myBus.register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        myBus.unregister(this);
    }

    private void init(View view) {
        ButterKnife.bind(this, view);
        rippleBackground = (RippleBackground) view.findViewById(R.id.content);
        infiniteRotateAnim = AnimationUtils.loadAnimation(getActivity(), R.anim.inf_rotate);
        myBus = ((RadioApplication) getActivity().getApplication()).getBus();
        mRadioStateModel = ((RadioApplication) getActivity().getApplication()).getRadioStateModel();
        updateViewsByRadioState(mRadioStateModel);
        bindCustomListeners();
    }

    private void bindCustomListeners() {
        volumeControl.setOnSectorChangedListener(this);
    }

    private void updateViewsByRadioState(RadioStateModel stateModel) {
        Log.d("BBB", "updating views with state model:" + stateModel.toString());
        txtSongInfo.setText(stateModel.getSongAuthor()
                .concat(" - ")
                .concat(stateModel.getSongTitle()));

        txtRadioState.setText(stateModel.getStateEnum().toString());
        refreshControlButtonDrawable(stateModel, infiniteRotateAnim);
    }

    private void refreshControlButtonDrawable(RadioStateModel stateModel, Animation animation) {
        //ovo ojačaj kod jer treba maknut rucno dodavanje na gumb animacije i sranja.
        if (stateModel.isMusicPlaying() && !stateModel.isStreamInterrupted()) {
            rippleBackground.startRippleAnimation();
        } else {
            rippleBackground.stopRippleAnimation();
        }
    }

    @Subscribe
    public void handleStreamStateChange(RadioStateEnum streamState) {
        switch (streamState) {
            case BUFFERING:
                txtRadioState.setText(RadioStateEnum.BUFFERING.toString());
                break;
            case ENDED:
                txtRadioState.setText(RadioStateEnum.ENDED.toString());
                rippleBackground.stopRippleAnimation();
                break;
            case IDLE:
                txtRadioState.setText(RadioStateEnum.IDLE.toString());
                rippleBackground.stopRippleAnimation();
                break;
            case PREPARING:
                txtRadioState.setText(RadioStateEnum.PREPARING.toString());
                break;
            case READY:
                //stop buffering animation if it exists
                txtRadioState.setText(RadioStateEnum.READY.toString());
                if (mRadioStateModel.isMusicPlaying() && !mRadioStateModel.isStreamInterrupted()) {
                    rippleBackground.startRippleAnimation();
                } else {
                    rippleBackground.stopRippleAnimation();
                }
                break;
            case UNKNOWN:
                txtRadioState.setText(RadioStateEnum.UNKNOWN.toString());
                break;
        }
    }

    @Subscribe
    public void handleSongMetadata(SongModel song) {
        //update view song data
        txtSongInfo.setText(song.getmAuthor().concat(" - ").concat(song.getTitle()));
        //update notification song data
        NotificationManager notificationManager =
                (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);

        Intent notificationIntent = new Intent(getActivity(), MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(getActivity(), 0,
                notificationIntent, 0);

        Notification notification = new Notification.Builder(getActivity())
                .setContentTitle("Antena Radio")
                .setContentText(song.getmAuthor().concat(" - ").concat(song.getTitle()))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .build();

        notification.flags |= Notification.FLAG_NO_CLEAR;
        notificationManager.notify(1337, notification);
    }

    @Override
    public void changeSector(int sectorID) {
        //Log.d("BBB", "sector id:" + sectorID);
        AudioManager audioManager =
                (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, sectorID, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
    }
}