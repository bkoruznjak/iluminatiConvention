package bkoruznjak.from.hr.antenazagreb.activity;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import com.github.ksoichiro.android.observablescrollview.ScrollUtils;
import com.mxn.soul.flowingdrawer_core.FlowingView;
import com.mxn.soul.flowingdrawer_core.LeftDrawerLayout;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.Locale;

import bkoruznjak.from.hr.antenazagreb.R;
import bkoruznjak.from.hr.antenazagreb.RadioApplication;
import bkoruznjak.from.hr.antenazagreb.adapters.AntenaPagerAdapter;
import bkoruznjak.from.hr.antenazagreb.bus.RadioBus;
import bkoruznjak.from.hr.antenazagreb.constants.PreferenceKeyConstants;
import bkoruznjak.from.hr.antenazagreb.constants.StreamUriConstants;
import bkoruznjak.from.hr.antenazagreb.enums.LanguagesEnum;
import bkoruznjak.from.hr.antenazagreb.enums.RadioCommandEnum;
import bkoruznjak.from.hr.antenazagreb.enums.RadioStateEnum;
import bkoruznjak.from.hr.antenazagreb.fragments.AntenaMenuFragment;
import bkoruznjak.from.hr.antenazagreb.model.bus.RadioStateModel;
import bkoruznjak.from.hr.antenazagreb.model.bus.RadioVolumeModel;
import bkoruznjak.from.hr.antenazagreb.model.network.ArticleModel;
import bkoruznjak.from.hr.antenazagreb.model.network.SocialModel;
import bkoruznjak.from.hr.antenazagreb.service.RadioService;
import bkoruznjak.from.hr.antenazagreb.views.AntenaTabFactory;
import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {

    static {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    private final int mBitmapWidth = 300;
    private final int mBitmapHeight = 399;
    @BindView(R.id.antenaTabLayout)
    TabLayout antenaTabLayout;
    @BindView(R.id.antenaToolbar)
    Toolbar antenaToolbar;
    @BindView(R.id.drawer_layout)
    LeftDrawerLayout drawerLayout;
    @BindView(R.id.floatingDrawer)
    FlowingView mFlowingView;
    @BindView(R.id.btnAntenaMainController)
    FloatingActionButton mBtnMainController;
    @BindView(R.id.fabUnderlay)
    ImageView fabUnderlayVector;
    @BindView(R.id.fab_main_stream)
    FloatingActionButton mBtnMainStream;
    @BindView(R.id.fab_rock_stream)
    FloatingActionButton mBtnRockStream;
    @BindView(R.id.fab_80_stream)
    FloatingActionButton mBtn80Stream;
    Animation infiniteRotateAnim;
    Animation rotateFrom0to90Animation;
    Animation rotateFrom90to0Animation;
    RadioStateModel mRadioStateModel;
    float densityPixelCoef;
    AnimatorSet moveMainStreamIcon;
    AnimatorSet moveRockStreamIcon;
    AnimatorSet move80sStreamIcon;
    AnimatorSet returnMainStreamIcon;
    AnimatorSet returnRockStreamIcon;
    AnimatorSet return80sStreamIcon;
    private RadioBus myBus;
    private boolean isRadioStationPickerShown = false;
    private SharedPreferences mPreferences;
    private AudioManager mAudioManager;
    private RadioVolumeModel mRadioVolume;
    private BitmapDrawable mBackgroundBitmap;
    private ArrayList<SocialModel> socialData;
    private ArrayList<ArticleModel> articleData;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPreferences = getSharedPreferences(PreferenceKeyConstants.PREFERENCE_NAME, MODE_PRIVATE);
        handleLocale();
        setContentView(R.layout.activity_main);
        init();
    }


    private void handleAutoPlay(boolean isAutoplayOn) {
        if (isAutoplayOn && !mRadioStateModel.isServiceUp()) {
            Log.d("BBB", "starting service anew due to autoplay");
            Intent startRadioServiceIntent = new Intent(getApplicationContext(), RadioService.class);
            startService(startRadioServiceIntent);
        }
    }

    private void handleLocale() {
        Locale locale = new Locale(mPreferences.getString(PreferenceKeyConstants.KEY_LANGUAGE, "hr"));
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.locale = locale;
        getBaseContext().getResources().updateConfiguration(config,
                getBaseContext().getResources().getDisplayMetrics());
    }

    @Override
    public void onResume() {
        super.onResume();
        myBus.register(this);
        handleAutoPlay(mPreferences.getBoolean(PreferenceKeyConstants.KEY_AUTOPLAY, true));

    }

    @Override
    public void onPause() {
        super.onPause();
        myBus.unregister(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!mRadioStateModel.isMusicPlaying() && mRadioStateModel.isServiceUp()) {
            Log.d("bbb", "app shutting down");
            myBus.post(RadioCommandEnum.STOP);
            Intent stopRadioServiceIntent = new Intent(getApplicationContext(), RadioService.class);
            stopService(stopRadioServiceIntent);
        }
        this.finishAffinity();
    }

    private void init() {
        ButterKnife.bind(this);
        mBackgroundBitmap = new BitmapDrawable(decodeSampledBitmapFromResource(getResources(), R.drawable.antena_bg, mBitmapWidth, mBitmapHeight));
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        myBus = ((RadioApplication) getApplication()).getBus();
        mRadioStateModel = ((RadioApplication) getApplication()).getRadioStateModel();
        setupAnimations();
        setupActionBar();
        setupTabBar();
        setupDrawer();
        setupFab();
        updateViewsByRadioState(mRadioStateModel);
    }

    /**
     * Overridden method to catch touch events outside the open floating drawer.
     * Touching the screen outside the drawer will close the same.
     *
     * @param event
     * @return
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && drawerLayout.isShownMenu()) {
            float floatingDrawerWidth = mFlowingView.getWidth();
            float touchX = event.getX();
            if (touchX > floatingDrawerWidth) {
                drawerLayout.closeDrawer();
                return false;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            drawerLayout.toggle();
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    /**
     * Overridden method to catch volume key input and notify all views to adjust acordingly
     *
     * @param event
     * @return
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
            int volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (mRadioVolume == null) {
                mRadioVolume = new RadioVolumeModel(volume);
            } else {
                mRadioVolume.setVolume(volume);
            }
            //Log.d("bbb","volume:" + volume);
            myBus.post(mRadioVolume);
            return super.dispatchKeyEvent(event);

        } else if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
            int volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (mRadioVolume == null) {
                mRadioVolume = new RadioVolumeModel(volume);
            } else {
                mRadioVolume.setVolume(volume);
            }
            //Log.d("bbb","volume:" + volume);
            myBus.post(mRadioVolume);
            return super.dispatchKeyEvent(event);
        }
        return super.dispatchKeyEvent(event);
    }


    private void setupAnimations() {
        densityPixelCoef = getResources().getDisplayMetrics().widthPixels / 100;
        //animation for main control fab
        infiniteRotateAnim = AnimationUtils.loadAnimation(this, R.anim.inf_rotate);
        //animation for vector under main control fab
        rotateFrom0to90Animation = AnimationUtils.loadAnimation(this, R.anim.rotate_0_90);
        rotateFrom90to0Animation = AnimationUtils.loadAnimation(this, R.anim.rotate_90_0);
        //animations for radio station fab's
        fabUnderlayVector.startAnimation(rotateFrom0to90Animation);
        //custom animation
        float mainTranslationX = -densityPixelCoef * 20f;
        float rockTranslationX = -densityPixelCoef * 14f;
        float rockTranslationY = -densityPixelCoef * 14f;
        float s80sTranslationY = -densityPixelCoef * 20f;

        //main stream animators
        ObjectAnimator moveXMain = ObjectAnimator.ofFloat(mBtnMainStream, View.TRANSLATION_X, mainTranslationX);
        ObjectAnimator revealMain = ObjectAnimator.ofFloat(mBtnMainStream, View.ALPHA, 1);
        ObjectAnimator returnMain = ObjectAnimator.ofFloat(mBtnMainStream, View.TRANSLATION_X, 0f);
        ObjectAnimator hideMain = ObjectAnimator.ofFloat(mBtnMainStream, View.ALPHA, 0);

        moveMainStreamIcon = new AnimatorSet();
        moveMainStreamIcon.play(moveXMain).with(revealMain);
        moveMainStreamIcon.setDuration(800);
        returnMainStreamIcon = new AnimatorSet();
        returnMainStreamIcon.play(returnMain).with(hideMain);
        returnMainStreamIcon.setDuration(1000);

        //rock stream animators
        ObjectAnimator moveXRock = ObjectAnimator.ofFloat(mBtnRockStream, View.TRANSLATION_X, rockTranslationX);
        ObjectAnimator moveYRock = ObjectAnimator.ofFloat(mBtnRockStream, View.TRANSLATION_Y, rockTranslationY);
        ObjectAnimator revealRock = ObjectAnimator.ofFloat(mBtnRockStream, View.ALPHA, 1);
        ObjectAnimator returnXRock = ObjectAnimator.ofFloat(mBtnRockStream, View.TRANSLATION_X, 0f);
        ObjectAnimator returnYRock = ObjectAnimator.ofFloat(mBtnRockStream, View.TRANSLATION_Y, 0f);
        ObjectAnimator hideRock = ObjectAnimator.ofFloat(mBtnRockStream, View.ALPHA, 0);

        moveRockStreamIcon = new AnimatorSet();
        moveRockStreamIcon.play(moveXRock).with(moveYRock).with(revealRock);
        moveRockStreamIcon.setDuration(1100);
        returnRockStreamIcon = new AnimatorSet();
        returnRockStreamIcon.play(returnXRock).with(returnYRock).with(hideRock);
        returnRockStreamIcon.setDuration(800);

        //80s stream animators
        ObjectAnimator moveY80s = ObjectAnimator.ofFloat(mBtn80Stream, View.TRANSLATION_Y, s80sTranslationY);
        ObjectAnimator reveal80s = ObjectAnimator.ofFloat(mBtn80Stream, View.ALPHA, 1);
        ObjectAnimator returnY80s = ObjectAnimator.ofFloat(mBtn80Stream, View.TRANSLATION_Y, 0f);
        ObjectAnimator hide80s = ObjectAnimator.ofFloat(mBtn80Stream, View.ALPHA, 0);

        move80sStreamIcon = new AnimatorSet();
        move80sStreamIcon.play(moveY80s).with(reveal80s);
        move80sStreamIcon.setDuration(1200);
        return80sStreamIcon = new AnimatorSet();
        return80sStreamIcon.play(returnY80s).with(hide80s);
        return80sStreamIcon.setDuration(600);
    }

    private void setupDrawer() {
        FragmentManager fm = getSupportFragmentManager();
        AntenaMenuFragment mMenuFragment = (AntenaMenuFragment) fm.findFragmentById(R.id.id_container_menu);
        if (mMenuFragment == null) {
            fm.beginTransaction().add(R.id.id_container_menu, mMenuFragment = new AntenaMenuFragment()).commit();
        }
        drawerLayout.setFluidView(mFlowingView);
        drawerLayout.setMenuFragment(mMenuFragment);
    }

    private void setupActionBar() {
        antenaToolbar.setBackgroundColor(ScrollUtils.getColorWithAlpha(0, getResources().getColor(R.color.colorPrimary)));
        antenaToolbar.setNavigationIcon(R.drawable.ic_menu_black_24dp);
        setSupportActionBar(antenaToolbar);

        antenaToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                drawerLayout.toggle();
            }
        });
    }

    private void setupFab() {
        mBtnMainController.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("bbb", "FAB CLICKED");
                if (mRadioStateModel.isServiceUp() && mRadioStateModel.isMusicPlaying() && !mRadioStateModel.isStreamInterrupted()) {
                    myBus.post(RadioCommandEnum.PAUSE);
                    mBtnMainController.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_arrow_white_24dp));
                    mBtnMainController.clearAnimation();
                } else if (mRadioStateModel.getStateEnum() == RadioStateEnum.BUFFERING) {
                    //todo ovo treba malo doradit, stavio sam tu samo da mozes prekinut buffeering na naglo
                    myBus.post(RadioCommandEnum.PAUSE);
                    mBtnMainController.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_arrow_white_24dp));
                    mBtnMainController.clearAnimation();
                } else if (mRadioStateModel.isServiceUp()) {
                    myBus.post(RadioCommandEnum.PLAY);
                } else {
                    Log.d("BBB", "starting service anew");
                    Intent startRadioServiceIntent = new Intent(getApplicationContext(), RadioService.class);
                    startService(startRadioServiceIntent);
                }
            }
        });

        mBtnMainController.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Log.d("bbb", "FAB LONG CLICKED");
                //todo stream choser
                if (isRadioStationPickerShown) {
                    //hide the cloud vector
                    fabUnderlayVector.startAnimation(rotateFrom0to90Animation);
                    returnMainStreamIcon.start();
                    return80sStreamIcon.start();
                    returnRockStreamIcon.start();
                    isRadioStationPickerShown = false;
                } else {
                    //show the cloud vector
                    fabUnderlayVector.startAnimation(rotateFrom90to0Animation);
                    moveMainStreamIcon.start();
                    moveRockStreamIcon.start();
                    move80sStreamIcon.start();
                    isRadioStationPickerShown = true;
                }

                return true;
            }
        });

        mBtnMainStream.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("bbb", "main stream pressed");
                handleStreamURI(StreamUriConstants.ANTENA_MAIN);
                refreshStreamButtons(StreamUriConstants.ANTENA_MAIN);
            }
        });

        mBtnRockStream.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("bbb", "rock stream pressed");
                handleStreamURI(StreamUriConstants.ANTENA_ROCK);
                refreshStreamButtons(StreamUriConstants.ANTENA_ROCK);
            }
        });

        mBtn80Stream.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("bbb", "80's stream pressed");
                handleStreamURI(StreamUriConstants.ANTENA_80);
                refreshStreamButtons(StreamUriConstants.ANTENA_80);
            }
        });
    }

    private void setupTabBar() {

        antenaTabLayout.addTab(antenaTabLayout.newTab());
        antenaTabLayout.addTab(antenaTabLayout.newTab());
        antenaTabLayout.addTab(antenaTabLayout.newTab());
        antenaTabLayout.addTab(antenaTabLayout.newTab());
        antenaTabLayout.addTab(antenaTabLayout.newTab());
        antenaTabLayout.setTabGravity(TabLayout.GRAVITY_FILL);

        AntenaTabFactory tabFactory = new AntenaTabFactory(this);
        antenaTabLayout.getTabAt(0).setCustomView(tabFactory.generateCustomTab(getResources().getString(R.string.promo_tab), getResources().getDrawable(R.drawable.ic_promo_border_white_24dp)));
        antenaTabLayout.getTabAt(1).setCustomView(tabFactory.generateCustomTab(getResources().getString(R.string.social_tab), getResources().getDrawable(R.drawable.ic_social_white_24dp)));
        antenaTabLayout.getTabAt(2).setCustomView(tabFactory.generateCustomTab(getResources().getString(R.string.radio_tab), getResources().getDrawable(R.drawable.ic_radio_white_24dp)));
        antenaTabLayout.getTabAt(3).setCustomView(tabFactory.generateCustomTab(getResources().getString(R.string.podcast_tab), getResources().getDrawable(R.drawable.ic_mic_white_24dp)));
        antenaTabLayout.getTabAt(4).setCustomView(tabFactory.generateCustomTab(getResources().getString(R.string.news_tab), getResources().getDrawable(R.drawable.ic_news_white_24dp)));

        final ViewPager viewPager = (ViewPager) findViewById(R.id.antenaViewPager);
        final AntenaPagerAdapter adapter = new AntenaPagerAdapter
                (getSupportFragmentManager(), antenaTabLayout.getTabCount());
        viewPager.setAdapter(adapter);
        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(antenaTabLayout));
        antenaTabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
        //set the initial load on the radio screen
        viewPager.setCurrentItem(2);
    }

    @Subscribe
    public void handleStreamStateChange(RadioStateEnum streamState) {
        switch (streamState) {
            case BUFFERING:
                mBtnMainController.setImageDrawable(getResources().getDrawable(R.drawable.ic_camera_white_24dp));
                Log.d("bbb", "starting infinite animation buffering");
                mBtnMainController.clearAnimation();
                mBtnMainController.startAnimation(infiniteRotateAnim);
                break;
            case ENDED:
                mBtnMainController.clearAnimation();
                mBtnMainController.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_arrow_white_24dp));
                break;
            case IDLE:
                mBtnMainController.clearAnimation();
                mBtnMainController.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_arrow_white_24dp));
                break;
            case PREPARING:
                mBtnMainController.setImageDrawable(getResources().getDrawable(R.drawable.ic_camera_white_24dp));
                Log.d("bbb", "starting infinite animation preparing");
                mBtnMainController.clearAnimation();
                mBtnMainController.startAnimation(infiniteRotateAnim);
                break;
            case READY:
                //stop buffering animation if it exists
                mBtnMainController.clearAnimation();
                if (mRadioStateModel.isMusicPlaying() && !mRadioStateModel.isStreamInterrupted()) {
                    mBtnMainController.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause_white_24dp));
                } else {
                    mBtnMainController.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_arrow_white_24dp));
                }
                break;
            case UNKNOWN:
                break;
        }
    }

    @Subscribe
    public void handleLanguageChange(LanguagesEnum languageEvent) {
        Log.d("bbb", "mijenjam jezik na:" + languageEvent.toString());
        Locale myLocale = new Locale(languageEvent.toString());
        Resources res = getResources();
        DisplayMetrics dm = res.getDisplayMetrics();
        Configuration conf = res.getConfiguration();
        conf.locale = myLocale;
        res.updateConfiguration(conf, dm);
        onConfigurationChanged(conf);
        Intent refresh = new Intent(getApplicationContext(), MainActivity.class);
        startActivity(refresh);
        finish();
    }

    @Subscribe
    public void handleData(final ArrayList data) {
        if (data.get(0) instanceof ArticleModel) {
            this.articleData = data;
        } else if (data.get(0) instanceof SocialModel) {
            this.socialData = data;
        }
    }

    private void refreshControlButtonDrawable(RadioStateModel stateModel, Animation animation) {
        //ovo ojačaj kod jer treba maknut rucno dodavanje na gumb animacije i sranja.
        if (stateModel.getStateEnum() == RadioStateEnum.BUFFERING || stateModel.getStateEnum() == RadioStateEnum.PREPARING) {
            mBtnMainController.setImageDrawable(getResources().getDrawable(R.drawable.ic_camera_white_24dp));
            Log.d("bbb", "starting infinite refresh");
            mBtnMainController.startAnimation(infiniteRotateAnim);
        } else if (stateModel.isMusicPlaying() && !stateModel.isStreamInterrupted()) {
            mBtnMainController.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause_white_24dp));
        } else {
            mBtnMainController.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_arrow_white_24dp));
        }
    }

    private void refreshStreamButtons(String radioStreamUri) {
        mBtnMainStream.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.md_deep_orange_500)));
        mBtnMainStream.setImageDrawable(getResources().getDrawable(R.drawable.ic_live_stream_icon_beige));
        mBtn80Stream.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.md_deep_orange_500)));
        mBtn80Stream.setImageDrawable(getResources().getDrawable(R.drawable.ic_80_stream_icon_beige));
        mBtnRockStream.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.md_deep_orange_500)));
        mBtnRockStream.setImageDrawable(getResources().getDrawable(R.drawable.ic_rock_stream_icon_beige));
        switch (radioStreamUri) {
            case StreamUriConstants.ANTENA_MAIN:
                mBtnMainStream.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.antena_beige)));
                mBtnMainStream.setImageDrawable(getResources().getDrawable(R.drawable.ic_live_stream_icon_orange));
                break;
            case StreamUriConstants.ANTENA_ROCK:
                mBtnRockStream.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.antena_beige)));
                mBtnRockStream.setImageDrawable(getResources().getDrawable(R.drawable.ic_rock_stream_icon_orange));
                break;
            case StreamUriConstants.ANTENA_80:
                mBtn80Stream.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.antena_beige)));
                mBtn80Stream.setImageDrawable(getResources().getDrawable(R.drawable.ic_80_stream_icon_orange));
                break;
        }
    }

    private void updateViewsByRadioState(RadioStateModel stateModel) {
        refreshControlButtonDrawable(stateModel, infiniteRotateAnim);
        refreshStreamButtons(stateModel.getStreamUri());
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

    private Bitmap decodeSampledBitmapFromResource(Resources res, int resId,
                                                   int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    private int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    public BitmapDrawable getBackgroundBitmap() {
        if (mBackgroundBitmap == null) {
            mBackgroundBitmap = new BitmapDrawable(decodeSampledBitmapFromResource(getResources(), R.drawable.antena_bg, mBitmapWidth, mBitmapHeight));
        }
        return mBackgroundBitmap;
    }

    public ArrayList<SocialModel> getSocialData() {
        return socialData;
    }

    public void setSocialData(ArrayList<SocialModel> socialData) {
        this.socialData = socialData;
    }

    public ArrayList<ArticleModel> getArticleData() {
        return articleData;
    }

    public void setArticleData(ArrayList<ArticleModel> articleData) {
        this.articleData = articleData;
    }
}
