package com.mrdan.eggsmash;

import android.animation.Animator;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.mikepenz.fontawesome_typeface_library.FontAwesome;
import com.mikepenz.iconics.IconicsDrawable;
import com.mrdan.eggsmash.interfaces.OnTrumpGoFragmentInteractionListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by danielMensah on 30/06/17.
 */

public class GameFragment extends Fragment implements SensorEventListener {

    private String TAG = "GameFragment";
    public static final float MOVE_FACTOR_X = 50f;
    public static final float MOVE_FACTOR_Y = MOVE_FACTOR_X * .8f;

    public static final float MOVE_THRESHOLD_ACCEL = 0.01f;
    public static final float MOVE_THRESHOLD_GYRO = 0.01f;

    public static final int STARTING_AR_OBJECT_COUNT = 3;

    public static final int FIRING_RATE = 200;
    public static final int IMPACT_VIBRATION = 50;
    public static final int ANIMATION_DURATION = 200;
    public static final int SHOT_DELAY = 200;
    public static final int TIME_TO_NEXT_AR = 1000; // Decrease for more trump heads
    public static final int TIME_TO_NEXT_VOTE = 200; // Decrease for faster electoral gameTimer
    public int TIME_TO_NEXT_ELECTION_DAY = 800; // Increase for longer games

    // Decrease for faster electoral gameTimer per trump head
    public static final float AR_COUNT_TIME_FACTOR = 3.5f;

    public static final int TRACKER_ARROW_SIZE = 80;
    public static final int BTC_SIZE = 200;
    public static final int BTC_SIZE_SPACING = BTC_SIZE + 50;

    private OnTrumpGoFragmentInteractionListener mListener;
    private View rootView;
    private ViewGroup inflateContainer;
    private LayoutInflater inflater;
    private View howToPlayContainer;

    private List<View> arObjects;
    private List<View> arLeftTrackerObjects;
    private List<View> arRightTrackerObjects;

    private List<Integer> lastLeftMargins;
    private List<Integer> lastTopMargins;
    private List<Boolean> btcShot;

    private AnimationSet laserAnimSet;

    private int height;
    private int width;
    private int topHitThreshold;
    private int bottomHitThreshold;
    private int leftHitThreshold;
    private int rightHitThreshold;

    private View laserView = null;

    private FrameLayout arContentOverlay = null;

    String accelData = "Accelerometer Data";
    String compassData = "Compass Data";
    String gyroData = "Gyro Data";
    private SensorManager sensorManager;

    private MediaPlayer laserMp;
    private MediaPlayer winMp;
    private MediaPlayer explosionMp;
    private MediaPlayer loseMp;

    private boolean arActive = false;
    private boolean gamePaused = false;
    private int gameTimer;
    private int totSmashedEggs;
    private int visibleArObjects;
    private int L1 = 40;
    private int L2 = 90;
    private int L3 = 180;
    private int L4 = 350;
    private int CURRENT_LEVEL = 1;

    private float accelX;
    private float accelY;
    private float accelZ;

    private float gyroX;
    private float gyroY;
    private float gyroZ;

    public Vibrator vibe;


    private Handler handler = new Handler();

    final Runnable autoGenerateArObjects = new Runnable() {
        @Override
        public void run() {
            createArObjectWithTrackers();
            calculateVisibleArObjects();
            int timeUntilNextArObject = Math.min(visibleArObjects * 100 + TIME_TO_NEXT_AR / 2,
                    TIME_TO_NEXT_AR);
            Log.d(TAG, "timeUntilNextArObject: " + timeUntilNextArObject);

            handler.postDelayed(autoGenerateArObjects, timeUntilNextArObject);
        }
    };

    final Runnable countElectoralVotes = new Runnable() {
        @Override
        public void run() {
            gameTimer += 1;
            ((TextView)rootView.findViewById(R.id.electoral_vote_counter)).setText(String.valueOf(gameTimer));
            int timeToNextVote = (int)Math.round((double)TIME_TO_NEXT_VOTE / (1 +
                    (visibleArObjects / AR_COUNT_TIME_FACTOR)));

            if(gameTimer > 200) {
                ((TextView)rootView.findViewById(R.id.electoral_vote_counter)).setTextColor
                        (ContextCompat.getColor(getContext(), R.color.primary));
            }

            switch (CURRENT_LEVEL) {
                case 1:
                    if (gameTimer == 270) {
                        gameOver();
                    } else {
                        continueGame(timeToNextVote);
                    }
                    break;
                case 2:
                    if (gameTimer == 570) {
                        gameOver();
                    } else {
                        continueGame(timeToNextVote);
                    }
                    break;
                case 3:
                    if (gameTimer == 770) {
                        gameOver();
                    } else {
                        continueGame(timeToNextVote);
                    }
                    break;
                case 4:
                    if (gameTimer == 900) {
                        gameOver();
                    } else {
                        continueGame(timeToNextVote);
                    }
                    break;
            }
        }
    };

    public void continueGame(int timeToNextVote) {
        handler.postDelayed(countElectoralVotes, timeToNextVote);
    }

    public void smashedEggs() {
        totSmashedEggs += 1;
        gameTimer -= 5;
        ((TextView)rootView.findViewById(R.id.days_to_election)).setText("" + totSmashedEggs);
    }

    final Runnable startFiring = new Runnable() {
        @Override
        public void run() {
            laserView.setVisibility(View.VISIBLE);

            laserView.startAnimation(laserAnimSet);
            vibe.vibrate(5);

            if(laserMp.isPlaying()) {
                laserMp.seekTo(0);
                laserMp.start();
            } else {
                laserMp.start();
            }
            handler.postDelayed(shotImpact, ANIMATION_DURATION);
            handler.postDelayed(startFiring, FIRING_RATE);
        }
    };

    final Runnable shotImpact = new Runnable() {
        @Override
        public void run() {
            checkForImpact();
        }
    };

    final Runnable calculateArMoves = new Runnable() {
        @Override
        public void run() {

        }
    };

    final Runnable moveArBtc = new Runnable() {
        @Override
        public void run() {
            if(gamePaused) return;
            setNewMargins();
        }
    };

//    public GameFragment() {
//        // Required empty public constructor
//    }
//
//    public void setUIArguments() {}

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnTrumpGoFragmentInteractionListener) {
            mListener = (OnTrumpGoFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG,"onCreate");
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG,"onCreateView");

        rootView = inflater.inflate(R.layout.game_fragment,container,false);

        inflateContainer = container;
        this.inflater = inflater;
        howToPlayContainer = rootView.findViewById(R.id.how_to_play_container);
        mListener.timeEvent("App Opened to Play Game");

        configureGameWindow();

        return rootView;
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG,"onPause");

        stopSensing();
    }

    @Override
    public void onStop() {
        Log.d(TAG,"onStop");
        stopAllRunnables();
        sensorManager.unregisterListener(this);
        handler.removeCallbacks(countElectoralVotes);
        handler.removeCallbacks(autoGenerateArObjects);

        if(winMp != null) winMp.stop();
        if(loseMp != null) loseMp.stop();
        if(laserMp != null) laserMp.stop();
        if(explosionMp != null) explosionMp.stop();


        super.onStop();
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG,"onDestroyView");
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG,"onDestroy");
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Log.d(TAG,"onDetach");
        mListener = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG,"onResume");
        rootView.findViewById(R.id.instructions_container).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.lose_container).setVisibility(View.GONE);
        rootView.findViewById(R.id.win_container).setVisibility(View.GONE);
        rootView.findViewById(R.id.game_hud_container).setVisibility(View.GONE);
        rootView.findViewById(R.id.camera_frame).setVisibility(View.GONE);
        rootView.findViewById(R.id.ar_content_overlay).setVisibility(View.GONE);

        assignClickListeners();
        applyCustomStyles();
        configureSensors();

    }

    private void applyCustomStyles()
    {
        ((ImageView)rootView.findViewById(R.id.crosshairs)).setImageDrawable(new
                IconicsDrawable(getContext())
                .icon(FontAwesome.Icon.faw_crosshairs)
                .color(ContextCompat.getColor(getContext(), R.color.white)));
        laserAnimSet = generateLaserAnimation();

    }

    private void assignClickListeners()
    {
        rootView.findViewById(R.id.play_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onLongClick: ");
                mListener.trackEvent("App Opened to Play Game");

                rootView.findViewById(R.id.instructions_container).setVisibility(View.GONE);
                initializeSound();
                initializeAr();
                prepareGame(0, 0);
            }
        });


        rootView.findViewById(R.id.shoot_button).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    startFiring.run();
                }
                if(motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    handler.removeCallbacks(startFiring);
                }
                return false;
            }
        });

        View.OnClickListener playAgain = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prepareGame(0, 0);
                mListener.trackEvent("Play Again Button Clicked");
            }
        };

        View.OnClickListener playNextLevel = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prepareGame(gameTimer, totSmashedEggs);
                mListener.trackEvent("Play Next Level Button Clicked");
            }
        };

        rootView.findViewById(R.id.play_again_win).setOnClickListener(playAgain);
        rootView.findViewById(R.id.play_again_lose).setOnClickListener(playAgain);
        rootView.findViewById(R.id.play_next_level).setOnClickListener(playNextLevel);

        View.OnClickListener shareApp = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                shareGeneral();
                mListener.trackEvent("Share Link Clicked");
            }
        };

        rootView.findViewById(R.id.share_win).setOnClickListener(shareApp);
        rootView.findViewById(R.id.share_lose).setOnClickListener(shareApp);

        View.OnClickListener harambeLink = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mListener.trackEvent("Harambe Shirt Link Clicked");
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://teespring.com/dems-out-for-harambe-2016#pid=2&cid=576&sid=front"));
                startActivity(browserIntent);
            }
        };

        rootView.findViewById(R.id.shirts_link_win).setOnClickListener(harambeLink);
        rootView.findViewById(R.id.shirts_link_lose).setOnClickListener(harambeLink);


        rootView.findViewById(R.id.how_to_play).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    howToPlayContainer.setVisibility(View.VISIBLE);
                    mListener.trackEvent("How to Play Clicked");

                    howToPlayContainer.animate().alpha(1f).setListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animator) {
                            howToPlayContainer.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onAnimationEnd(Animator animator) {

                        }

                        @Override
                        public void onAnimationCancel(Animator animator) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animator) {

                        }
                    });
                }
                return false;
            }
        });
        rootView.findViewById(R.id.dismiss_how_to_play).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                howToPlayContainer.animate().alpha(0f).setListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animator) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        howToPlayContainer.setVisibility(View.GONE);

                    }

                    @Override
                    public void onAnimationCancel(Animator animator) {

                    }

                    @Override
                    public void onAnimationRepeat(Animator animator) {

                    }
                });

            }
        });

    }

    private void calculateVisibleArObjects() {
        int numberOfBtcShot = 0;

        for(int i = 0; i < btcShot.size(); i++) {
            if(btcShot.get(i)) numberOfBtcShot++;
        }

        visibleArObjects = btcShot.size() - numberOfBtcShot;
    }

    private void checkForImpact() {
        for(int i = 0; i < btcShot.size(); i++) {
            int leftMargin = lastLeftMargins.get(i);
            int topMargin = lastTopMargins.get(i);

            if(!btcShot.get(i) && topMargin > topHitThreshold && topMargin < bottomHitThreshold &&
                    leftMargin < rightHitThreshold && leftMargin > leftHitThreshold) {
                final View arObject = arObjects.get(i);

                ((ImageView)arObject.findViewById(R.id.ar_object_image)).setImageResource(R
                        .mipmap.crackedegg);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        arObject.setVisibility(View.GONE);
                        smashedEggs();

                    }
                }, SHOT_DELAY);
                vibe.vibrate(IMPACT_VIBRATION);
                playRandomQuote();
                btcShot.set(i, true);
            }
        }
    }


    private void configureGameWindow() {
        arContentOverlay = (FrameLayout)rootView.findViewById(R.id.ar_content_overlay);
        laserView = rootView.findViewById(R.id.laser);
        Display display = getActivity().getWindowManager().getDefaultDisplay();
        height = display.getHeight();
        width = display.getWidth();
        topHitThreshold = height/2 - (int)(BTC_SIZE*1.1);
        bottomHitThreshold = height/2;
        leftHitThreshold = width/2 - (int)(BTC_SIZE*.9);
        rightHitThreshold = width/2;
    }

    private void configureSensors()
    {
        vibe = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);

        sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        Sensor gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        boolean isGyroAvailable = sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
        boolean isAccelAvailable = sensorManager.registerListener(this, accelSensor, SensorManager
                .SENSOR_DELAY_GAME);

        Log.d(TAG, "isGyroAvailable: " +isGyroAvailable);
        Log.d(TAG, "isAccelAvailable: " +isAccelAvailable);

    }

    private void createArObjectWithTrackers() {
        btcShot.add(false);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams
                .WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int leftMargin = BTC_SIZE_SPACING + (int)(Math.random() * 1500 * (Math.random() >
                0.5 ? -1 : 1));
        int topMargin = BTC_SIZE_SPACING + (int)(Math.random() * 500 * (Math.random() > 0.5
                ? -1 : 1) + (height / 4));
        lastLeftMargins.add(leftMargin);
        lastTopMargins.add(topMargin);

        View arLayout = inflater.inflate(R.layout.ar_object_trump_view, inflateContainer, false);

        params.setMargins(leftMargin, topMargin, 0, 0);
        arLayout.setLayoutParams(params);
        arContentOverlay.addView(arLayout);
        arObjects.add(arLayout);


        View leftTracker = inflater.inflate(R.layout.ar_object_left_tracker_view, inflateContainer,
                false);
        View rightTracker = inflater.inflate(R.layout.ar_object_right_tracker_view, inflateContainer,
                false);
        ((ImageView)leftTracker.findViewById(R.id.ar_left_tracker_arrow_image)).setImageDrawable(new
                IconicsDrawable(getContext())
                .icon(FontAwesome.Icon.faw_angle_left)
                .color(ContextCompat.getColor(getContext(), R.color.accent)));
        ((ImageView)rightTracker.findViewById(R.id.ar_right_tracker_arrow_image)).setImageDrawable(new
                IconicsDrawable(getContext())
                .icon(FontAwesome.Icon.faw_angle_right)
                .color(ContextCompat.getColor(getContext(), R.color.accent)));

        FrameLayout.LayoutParams arrowParams = new FrameLayout.LayoutParams(ViewGroup
                .LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        leftTracker.setLayoutParams(arrowParams);
        rightTracker.setLayoutParams(arrowParams);
        arContentOverlay.addView(leftTracker);
        arContentOverlay.addView(rightTracker);
        arLeftTrackerObjects.add(leftTracker);
        arRightTrackerObjects.add(rightTracker);
    }

    private void initializeAr() {
        arActive = true;

        arContentOverlay.setVisibility(View.VISIBLE);

        FrameLayout arViewPane = (FrameLayout) rootView.findViewById(R.id.camera_frame);
        arViewPane.removeAllViews();
        ArDisplayView arDisplay = new ArDisplayView(getContext());
        arViewPane.addView(arDisplay);
        arViewPane.setVisibility(View.VISIBLE);

    }

    private void initializeGameTimers(int seconds, int smashedEggs) {
        totSmashedEggs = smashedEggs;
        gameTimer = seconds;
        countElectoralVotes.run();
    }

    private void initializeSound() {
        laserMp = MediaPlayer.create(getContext(), R.raw.laser);
        winMp = MediaPlayer.create(getContext(), R.raw.win);
        explosionMp = MediaPlayer.create(getContext(), R.raw.explosion);
        loseMp = MediaPlayer.create(getContext(), R.raw.lose);

        laserMp.setVolume(0.07f, 0.07f);


    }

    private void gameOver() {

        rootView.findViewById(R.id.shoot_button).setVisibility(View.GONE);
        rootView.findViewById(R.id.crosshairs).setVisibility(View.GONE);
        for(int i = 0; i < btcShot.size(); i++) {
            arLeftTrackerObjects.get(i).setVisibility(View.GONE);
            arRightTrackerObjects.get(i).setVisibility(View.GONE);
        }
        handler.removeCallbacks(autoGenerateArObjects);
        handler.removeCallbacks(countElectoralVotes);

        if (CURRENT_LEVEL == 1) {
            if (totSmashedEggs > L1) {
                nextLevel();
            } else {
                lostGame();
            }
        } else if (CURRENT_LEVEL == 2) {
            if (totSmashedEggs > L2) {
                nextLevel();
            } else {
                lostGame();
            }
        } else if (CURRENT_LEVEL == 3) {
            if (totSmashedEggs > L3) {
                nextLevel();
            } else {
                lostGame();
            }
        } else if (CURRENT_LEVEL == 4) {
            if (totSmashedEggs > L4) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        rootView.findViewById(R.id.win_container).setVisibility(View.VISIBLE);
                        mListener.trackEvent("Game Won");
                        winMp.start();
                    }
                }, 500);
                CURRENT_LEVEL = 1;
            } else {
                lostGame();
            }
        }

//        if(totSmashedEggs > 35) {
//            handler.postDelayed(new Runnable() {
//                @Override
//                public void run() {
//                    rootView.findViewById(R.id.win_container).setVisibility(View.VISIBLE);
//                    mListener.trackEvent("Game Won");
//                    winMp.start();
//                }
//            }, 500);
//        } else {
//            handler.postDelayed(new Runnable() {
//                @Override
//                public void run() {
//                    rootView.findViewById(R.id.lose_container).setVisibility(View.VISIBLE);
//                    mListener.trackEvent("Game Lost");
//                    loseMp.start();
//                }
//            }, 500);
//        }

        gamePaused = true;
    }

    public void nextLevel() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                rootView.findViewById(R.id.next_level_container).setVisibility(View.VISIBLE);
                mListener.trackEvent("Game Next Level");
                winMp.start();
            }
        }, 500);
        CURRENT_LEVEL += 1;
    }

    public void lostGame() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                rootView.findViewById(R.id.lose_container).setVisibility(View.VISIBLE);
                mListener.trackEvent("Game Lost");
                loseMp.start();
            }
        }, 500);

        CURRENT_LEVEL = 1;
    }

    private void generateArObjects() {

        arObjects = new ArrayList<>();
        arLeftTrackerObjects = new ArrayList<>();
        arRightTrackerObjects = new ArrayList<>();
        lastLeftMargins = new ArrayList<>();
        lastTopMargins = new ArrayList<>();
        btcShot = new ArrayList<>();

        for(int i = 0; i < STARTING_AR_OBJECT_COUNT; i++) {
            createArObjectWithTrackers();
        }

        autoGenerateArObjects.run();

        gamePaused = false;

    }

    private AnimationSet generateLaserAnimation() {
        AnimationSet animSet = new AnimationSet(true);

        ScaleAnimation scaleAnimationShrink = new ScaleAnimation(10f, 0, 20f, 0, Animation
                .RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        scaleAnimationShrink.setFillAfter(true);

        scaleAnimationShrink.setDuration(ANIMATION_DURATION);

        TranslateAnimation transAnim = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0f,
                Animation.RELATIVE_TO_PARENT, 0f,
                Animation.RELATIVE_TO_PARENT, 0.45f,
                Animation.RELATIVE_TO_PARENT, 0.025f);
        transAnim.setFillAfter(true);
        transAnim.setInterpolator(new DecelerateInterpolator(1000f));

        transAnim.setDuration(ANIMATION_DURATION);

        animSet.addAnimation(transAnim);
        animSet.addAnimation(scaleAnimationShrink);
        animSet.setFillAfter(true);
        animSet.setInterpolator(new DecelerateInterpolator(1f));
        return animSet;
    }

    private void moveArFrameGyro(float x, float y, float z) {

        if(x > MOVE_THRESHOLD_GYRO) {
            ArrayList<Integer> newTopMargins = new ArrayList<>();

            for(int i = 0; i < arObjects.size(); i++) {
                int topMargin = lastTopMargins.get(i) + (int)(1 + MOVE_FACTOR_X * x);
                newTopMargins.add(topMargin);
            }
            lastTopMargins = newTopMargins;

            handler.post(moveArBtc);

        }
        if(x < MOVE_THRESHOLD_GYRO * -1) {

            ArrayList<Integer> newTopMargins = new ArrayList<>();

            for(int i = 0; i < arObjects.size(); i++) {
                int topMargin = lastTopMargins.get(i) - (int)(1 + MOVE_FACTOR_X * x * -1);
                newTopMargins.add(topMargin);
            }
            lastTopMargins = newTopMargins;

            handler.post(moveArBtc);

        }

        if(y > MOVE_THRESHOLD_GYRO) {

            ArrayList<Integer> newLeftMargins = new ArrayList<>();

            for(int i = 0; i < arObjects.size(); i++) {
                int leftMargin = lastLeftMargins.get(i) + (int)(1 + MOVE_FACTOR_Y * y);
                newLeftMargins.add(leftMargin);
            }
            lastLeftMargins = newLeftMargins;

            handler.post(moveArBtc);

        }

        if(y < MOVE_THRESHOLD_GYRO * -1) {

            ArrayList<Integer> newLeftMargins = new ArrayList<>();

            for(int i = 0; i < arObjects.size(); i++) {
                int leftMargin = lastLeftMargins.get(i) - (int)(1 + MOVE_FACTOR_Y * y * -1);
                newLeftMargins.add(leftMargin);
            }
            lastLeftMargins = newLeftMargins;

            handler.post(moveArBtc);

        }

    }

    private void moveArFrameAccel(float x, float y, float z) {

        if(x > MOVE_THRESHOLD_GYRO) {
            Log.d(TAG, "Move down: " + String.format("%.4f", x));

            handler.post(moveArBtc);

        }
        if(x < MOVE_THRESHOLD_GYRO * -1) {
            Log.d(TAG, "Move up: " + String.format("%.4f", x));
            handler.post(moveArBtc);

        }

        if(y > MOVE_THRESHOLD_GYRO) {
            Log.d(TAG, "Move right: " + String.format("%.4f", y));
            handler.post(moveArBtc);

        }

        if(y < MOVE_THRESHOLD_GYRO * -1) {
            Log.d(TAG, "Move left: " + String.format("%.4f", y));


            handler.post(moveArBtc);

        }

    }

    private void playRandomQuote() {
        explosionMp.start();
    }

    private void prepareGame(int initializeGameTimer, int initializeSmashedEggs) {
        arContentOverlay.removeAllViews();

        rootView.findViewById(R.id.instructions_container).setVisibility(View.GONE);
        rootView.findViewById(R.id.how_to_play_container).setVisibility(View.GONE);
        rootView.findViewById(R.id.win_container).setVisibility(View.GONE);
        rootView.findViewById(R.id.lose_container).setVisibility(View.GONE);
        rootView.findViewById(R.id.next_level_container).setVisibility(View.GONE);
        rootView.findViewById(R.id.game_hud_container).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.shoot_button).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.crosshairs).setVisibility(View.VISIBLE);
        ((TextView)rootView.findViewById(R.id.electoral_vote_counter)).setTextColor
                (ContextCompat.getColor(getContext(), R.color.white));

        generateArObjects();
        initializeGameTimers(initializeGameTimer, initializeSmashedEggs);
    }

    private void setNewMargins() {
        for(int i = 0; i < arObjects.size(); i++) {
            View arView = arObjects.get(i);
            int leftMargin = lastLeftMargins.get(i);
            int topMargin =lastTopMargins.get(i);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(arView.getLayoutParams());
            params.setMargins(leftMargin, topMargin, 0, 0);
            arView.setLayoutParams(params);
            if(!btcShot.get(i)) {
                View leftTrackerArrow = arLeftTrackerObjects.get(i);
                View rightTrackerArrow = arRightTrackerObjects.get(i);

                if(leftMargin < BTC_SIZE * -1) {
                    leftTrackerArrow.setVisibility(View.VISIBLE);
                    FrameLayout.LayoutParams leftTrackerArrowParams = new FrameLayout
                            .LayoutParams(leftTrackerArrow.getLayoutParams());
                    leftTrackerArrowParams.setMargins(0, Math.max(0, topMargin), 0, 0);
                    leftTrackerArrow.setLayoutParams(leftTrackerArrowParams);
                } else {
                    leftTrackerArrow.setVisibility(View.INVISIBLE);
                }
                if(leftMargin > width) {
                    rightTrackerArrow.setVisibility(View.VISIBLE);
                    FrameLayout.LayoutParams rightTrackerArrowParams = new FrameLayout
                            .LayoutParams(rightTrackerArrow.getLayoutParams());
                    rightTrackerArrowParams.setMargins(width - TRACKER_ARROW_SIZE, Math.max
                            (0, topMargin), 0, 0);
                    rightTrackerArrow.setLayoutParams(rightTrackerArrowParams);

                } else {
                    rightTrackerArrow.setVisibility(View.INVISIBLE);
                }
            }
        }
    }

    private void shareGeneral() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=" + getContext
                ().getPackageName());
        startActivity(Intent.createChooser(shareIntent, "Share link using"));
    }

//    private void startVideo() {
//        landingVideo = (VideoView)rootView.findViewById(R.id.landing_video);
//        landingVideo.setVideoURI(Uri.parse("android.resource://" + getContext().getPackageName()
//                + "/" + R.raw.landingvideo));
//        landingVideo.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
//            @Override
//            public void onPrepared(MediaPlayer mediaPlayer) {
//                mediaPlayer.setLooping(true);
//                mediaPlayer.setVolume(0, 0);
//                landingVideo.start();
//            }
//        });
//        landingVideo.setVisibility(View.VISIBLE);
//
//    }

    private void stopAllRunnables()
    {
    }

    private void stopSensing() {
        sensorManager.unregisterListener(this);
    }

    private void updateSensorUi() {

//        ((TextView)rootView.findViewById(R.id.accel_text_x)).setText(String.format("%.4f", accelX));
//        ((TextView)rootView.findViewById(R.id.accel_text_y)).setText(String.format("%.4f", accelY));
//        ((TextView)rootView.findViewById(R.id.accel_text_z)).setText(String.format("%.4f", accelZ));
//
//        ((TextView)rootView.findViewById(R.id.gyro_text_x)).setText(String.format("%.4f", gyroX));
//        ((TextView)rootView.findViewById(R.id.gyro_text_y)).setText(String.format("%.4f", gyroY));
//        ((TextView)rootView.findViewById(R.id.gyro_text_z)).setText(String.format("%.4f", gyroZ));
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        switch(sensorEvent.sensor.getType())
        {
            case Sensor.TYPE_LINEAR_ACCELERATION:
                accelX = sensorEvent.values[0];
                accelY = sensorEvent.values[1];
                accelZ = sensorEvent.values[2];

                updateSensorUi();
//                if(arActive) moveArFrameAccel(accelX, accelY, accelZ);

                break;
            case Sensor.TYPE_GYROSCOPE:

                gyroX = sensorEvent.values[0];
                gyroY = sensorEvent.values[1];
                gyroZ = sensorEvent.values[2];

                updateSensorUi();
                if(arActive) moveArFrameGyro(gyroX, gyroY, gyroZ);

                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        Log.d(TAG, "onAccuracyChanged");
        Log.d(TAG, sensor.toString());

        switch(sensor.getType())
        {
            case Sensor.TYPE_ACCELEROMETER:
                break;
            case Sensor.TYPE_GYROSCOPE:
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                break;
        }

    }
}