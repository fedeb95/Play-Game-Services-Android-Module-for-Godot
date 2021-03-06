package org.godotengine.godot;

import android.util.Log;
import android.view.View;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.games.Games;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.common.ConnectionResult;
import android.content.IntentSender.SendIntentException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;

public class PlayGameServices extends Godot.SingletonBase {

    private static final int RC_SAVED_GAMES = 9002;
    private static final int RC_SIGN_IN = 9001;
    private static final int REQUEST_ACHIEVEMENTS = 9002;
    private static final int REQUEST_LEADERBOARD = 1002;
    private int deviceID;
    private Activity activity;
    private GoogleApiClient client;
    private Boolean isRequestingSignIn = false;
    private Boolean isIntentInProgress = false;
    private Boolean isGooglePlayConnected = false;
    private Boolean isResolvingConnectionFailure = false;
    private String leaderBoardID = null;
    private int leaderBoardScore = 0;


    static public Godot.SingletonBase initialize(Activity p_activity) { return new PlayGameServices(p_activity); }

    public PlayGameServices(Activity p_activity) {
        activity = p_activity;
        registerClass("PlayGameServices", new String[]{"init","sign_in","leaderboard_submit","leaderboard_show","achievement_unlock","achievement_increment","achievement_show_list","is_logged_in","sign_out"});
    }
     
    protected void onMainDestroy() {
        disconnect();
    }

    public void init(int myDeviceID) {
        deviceID = myDeviceID;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                client = new GoogleApiClient.Builder(activity)
                .addConnectionCallbacks(new ConnectionCallbacks(){
                    @Override
                    public void onConnected(Bundle m_bundle) {
                        Log.i("godot", "PlayGameServices: connection callbacks onConnected ");
                        isGooglePlayConnected = true;
                        Log.i("godot", "PlayGameServices: calling godot above ");
                        GodotLib.calldeferred(deviceID, "_on_play_services_connected", new Object[0]);
                    }
                    @Override
                    public void onConnectionSuspended(int m_cause) {
                        Log.i("godot", "PlayGameServices: connection callbacks onConnectionSuspended int cause "+String.valueOf(m_cause));
                        GodotLib.calldeferred(deviceID, "_on_play_services_connection_suspended", new Object[0]);
                    }
                })
                .addOnConnectionFailedListener(new OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult m_result) {
                        Log.i("godot", "PlayGameServices: onConnectionFailed result code: "+String.valueOf(m_result));
						
                        if (isResolvingConnectionFailure) {
                            return;
                        }
                        
						if(!isIntentInProgress && m_result.hasResolution()) {
                            try {
                                isIntentInProgress = true;
                                activity.startIntentSenderForResult(m_result.getResolution().getIntentSender(), RC_SIGN_IN, null, 0, 0, 0);
                            } catch (SendIntentException ex) {
                                isIntentInProgress = false;
                                client.connect();                                                          
                            }
                            isResolvingConnectionFailure = true;      
                        }
                    }
                })
                .addApi(Games.API).addScope(Games.SCOPE_GAMES)
                .build();
            }
        });
    }

    public void sign_in() {        
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {                
                client.connect();                
            }
        });

        Log.i("godot", "PlayGameServices: signing in to Google Play Game Services... GoogleAPIClient.connect()");
    }

    public void sign_out() {
        disconnect();
    }

    public void disconnect() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(client.isConnected()) {
                    Games.signOut(client);
                    client.disconnect();
                    isGooglePlayConnected = false;
                }
                Log.i("godot", "PlayGameServices: disconnecting from Google Play Game Services...");
            }
        });
    }
    
    public void is_logged_in() {            
        GodotLib.calldeferred(deviceID, "is_user_logged_in", new Object[]{ isGooglePlayConnected });
    }    

    public void leaderboard_submit(String id, int score) {
        leaderBoardID = id;
        leaderBoardScore = score;
        
        sign_in();
        
        if (isGooglePlayConnected) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() { Games.Leaderboards.submitScore(client, leaderBoardID, leaderBoardScore);
                }
            });

            Log.i("godot", "PlayGameServices: leaderboard_submit");
        } else {
            Log.i("godot", "PlayGameServices: trying to call Google Play Game Services before connected, try calling sign_in first");            
            return;
        }
    }

    public void leaderboard_show(String id) {
        leaderBoardID = id;
        
        sign_in();

        if (isGooglePlayConnected) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() { activity.startActivityForResult(Games.Leaderboards.getLeaderboardIntent(client, leaderBoardID), REQUEST_LEADERBOARD); }
            });

            Log.i("godot", "PlayGameServices: leaderboard_show");
        } else {
            Log.i("godot", "PlayGameServices: trying to call Google Play Game Services before connected, try calling sign_in first");
            return;
        }
    }

    public void achievement_unlock(final String achievement_id) {
        sign_in();
        
        if(isGooglePlayConnected) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Games.Achievements.unlock(client, achievement_id);
                }
            });

            Log.i("godot", "PlayGameServices: achievement_unlock");
        } else {
            Log.i("godot", "PlayGameServices: trying to call Google Play Game Services before connected, try calling sign_in first");
            return;
        }
    }

    public void achievement_increment(final String achievement_id, final int increment_amount) {
        sign_in();
        
        if(isGooglePlayConnected) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Games.Achievements.increment(client, achievement_id, increment_amount);
                }
            });
        } else {
            Log.i("godot", "PlayGameServices: trying to call Google Play Game Services before connected, try calling sign_in first");
            return;
        }
    }

    public void achievement_show_list() {
        sign_in();
        
        if(isGooglePlayConnected) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.startActivityForResult(Games.Achievements.getAchievementsIntent(client), REQUEST_ACHIEVEMENTS);
                }
            });
        } else {
            Log.i("godot", "PlayGameServices: trying to call Google Play Game Services before connected, try calling sign_in first");
            return;
        }
    }  

    protected void onMainActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == RC_SIGN_IN) {
            isIntentInProgress = false;

            if(!client.isConnecting()) {
                client.connect();
            }
        }
    }
}






