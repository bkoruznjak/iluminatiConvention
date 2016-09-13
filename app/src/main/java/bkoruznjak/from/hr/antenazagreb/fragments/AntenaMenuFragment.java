package bkoruznjak.from.hr.antenazagreb.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.mxn.soul.flowingdrawer_core.MenuFragment;
import com.squareup.otto.Subscribe;
import com.squareup.picasso.Picasso;

import bkoruznjak.from.hr.antenazagreb.R;
import bkoruznjak.from.hr.antenazagreb.RadioApplication;
import bkoruznjak.from.hr.antenazagreb.bus.RadioBus;
import bkoruznjak.from.hr.antenazagreb.constants.PreferenceKeyConstants;
import bkoruznjak.from.hr.antenazagreb.constants.StreamUriConstants;
import bkoruznjak.from.hr.antenazagreb.model.bus.RadioStateModel;
import bkoruznjak.from.hr.antenazagreb.model.db.SongModel;
import bkoruznjak.from.hr.antenazagreb.views.CircleTransformation;
import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by bkoruznjak on 08/08/16.
 */
public class AntenaMenuFragment extends MenuFragment {


    @BindView(R.id.setting_autoplay)
    public SwitchCompat settingAutoplay;
    @BindView(R.id.setting_keep_articles)
    public SwitchCompat settingKeepArticles;
    @BindView(R.id.ivMenuUserProfilePhoto)
    public ImageView drawerImage;
    @BindView(R.id.radioStationNameId)
    public TextView radioStationText;
    @BindView(R.id.songNameId)
    public TextView currentlyPlayingText;
    @BindView(R.id.setting_about)
    public Button buttonAbout;
    @BindView(R.id.setting_feedback)
    public Button buttonFeedback;
    @BindView(R.id.setting_third_party)
    public Button buttonThirdParty;

    private RadioBus myBus;
    private RadioStateModel mRadioStateModel;
    private SharedPreferences mPreferences;

    private boolean isAutoplayOn;
    private boolean isStoringArticleData;
    private String defaultStation;
    private int volume;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myBus = ((RadioApplication) getActivity().getApplication()).getBus();
        mRadioStateModel = ((RadioApplication) getActivity().getApplication()).getRadioStateModel();
        mPreferences = getActivity().getSharedPreferences(PreferenceKeyConstants.PREFERENCE_NAME, Context.MODE_PRIVATE);

        isAutoplayOn = mPreferences.getBoolean(PreferenceKeyConstants.KEY_AUTOPLAY, true);
        isStoringArticleData = mPreferences.getBoolean(PreferenceKeyConstants.KEY_STORE_ARTICLES, false);
        defaultStation = mPreferences.getString(PreferenceKeyConstants.KEY_DEFAULT_STATION, StreamUriConstants.ANTENA_MAIN);
        volume = mPreferences.getInt(PreferenceKeyConstants.KEY_VOLUME, 5);
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View drawerMenu = inflater.inflate(R.layout.fragment_drawer_menu, container, false);
        ButterKnife.bind(this, drawerMenu);
        final NavigationView naviView = (NavigationView) drawerMenu.findViewById(R.id.vNavigation);
        ImageView drawerHeaderImage = (ImageView) drawerMenu.findViewById(R.id.ivMenuUserProfilePhoto);
        setupHeader(drawerHeaderImage);

        //radio and song information
        radioStationText.setText(mRadioStateModel.getRadioStationName());
        currentlyPlayingText.setText(mRadioStateModel.getSongAuthor().concat(" - ").concat(mRadioStateModel.getSongTitle()));
        currentlyPlayingText.setSelected(true);
        //switch settings department
        if (isAutoplayOn) {
            settingAutoplay.setChecked(true);
        } else {
            settingAutoplay.setChecked(false);
        }
        settingAutoplay.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mPreferences.edit().putBoolean(PreferenceKeyConstants.KEY_AUTOPLAY, isChecked).commit();
            }
        });
        if (isStoringArticleData) {
            settingKeepArticles.setChecked(true);
        } else {
            settingKeepArticles.setChecked(false);
        }
        settingKeepArticles.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mPreferences.edit().putBoolean(PreferenceKeyConstants.KEY_STORE_ARTICLES, isChecked).commit();
            }
        });
        //other info department
        buttonAbout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("bbb", "idem na about");
                Toast.makeText(getActivity(), "about clicked", Toast.LENGTH_SHORT).show();
            }
        });
        buttonFeedback.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("bbb", "idem na feedback");
                Toast.makeText(getActivity(), "feedback clicked", Toast.LENGTH_SHORT).show();
            }
        });
        buttonThirdParty.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("bbb", "idem na third party");
                Toast.makeText(getActivity(), "third party clicked", Toast.LENGTH_SHORT).show();
            }
        });

        return setupReveal(drawerMenu);
    }

    @Subscribe
    public void handleSongMetadata(SongModel song) {
        radioStationText.setText(mRadioStateModel.getRadioStationName());
        currentlyPlayingText.setText(mRadioStateModel.getSongAuthor().concat(" - ").concat(mRadioStateModel.getSongTitle()));
        currentlyPlayingText.setSelected(true);
    }

    /**
     * Insurance method in case the service is down, user is still able to change streams
     *
     * @param streamURI
     */
    private void handleStreamURI(String streamURI) {
        Log.d("BBB", "StreamURI:" + streamURI);
        if (mRadioStateModel.isServiceUp()) {
            myBus.post(streamURI);
        } else {
            mRadioStateModel.setStreamUri(streamURI);
        }
    }

    private void setupHeader(ImageView ivMenuUserProfilePhoto) {
        int avatarSize = getResources().getDimensionPixelSize(R.dimen.item_height);
        Picasso.with(getActivity())
                .load(R.drawable.random_background_image)
                .placeholder(R.drawable.img_placeholder)
                .resize(avatarSize, avatarSize)
                .centerCrop()
                .transform(new CircleTransformation())
                .into(ivMenuUserProfilePhoto);
    }

    public void onOpenMenu() {
        //Toast.makeText(getActivity(),"onOpenMenu",Toast.LENGTH_SHORT).show();
    }

    public void onCloseMenu() {
        //Toast.makeText(getActivity(),"onCloseMenu",Toast.LENGTH_SHORT).show();
    }
}