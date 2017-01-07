package com.zegome.utils.facebook;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.Profile;
import com.facebook.ProfileTracker;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.share.ShareApi;
import com.facebook.share.Sharer.Result;
import com.facebook.share.model.ShareLinkContent;
import com.facebook.share.model.SharePhoto;
import com.facebook.share.model.SharePhotoContent;
import com.facebook.share.widget.ShareDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FacebookHelper implements IFacebook {
    private static final String PERMISSION = "publish_actions";

    public static final String SHARE_BUNDLE_NAME = "name";
    public static final String SHARE_BUNDLE_TITLE = "title";
    public static final String SHARE_BUNDLE_CAPTION = "caption";
    public static final String SHARE_BUNDLE_URL_CONTENT = "url_content";
    public static final String SHARE_BUNDLE_URL_IMAGE = "url_image";
    public static final String SHARE_BUNDLE_DESCRIPTION = "description";

    public interface ProfileTrackerCallback {
        void onCurrentProfileChanged(Profile older, Profile newer);
    }

    //global refs
    //callbacks
//	private TaskCallback mLoginCallback;
    private CallbackManager mCallbackManager;
    //    private FacebookCallback<LoginResult> mFacebookCallback;
//    private List<String> mPermissions = Arrays.asList("email");
    private ProfileTracker profileTracker;
    private LoginManager mLoginManager;
    private Activity mActivity;

    private ProfileTrackerCallback mProfileTrackerCallback = null;

    private boolean canShareLink = false;
    private boolean canSharePhoto = false;

    public FacebookHelper(final Activity ac) {
        mActivity = ac;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        //code is inside method
        FacebookSdk.sdkInitialize(mActivity.getApplicationContext());
        AppEventsLogger.activateApp(mActivity);

        mCallbackManager = CallbackManager.Factory.create();
        mLoginManager = LoginManager.getInstance();

        // Can we present the share dialog for regular links?
        canShareLink = ShareDialog.canShow(ShareLinkContent.class);

        // Can we present the share dialog for photos?
        canSharePhoto = ShareDialog.canShow(SharePhotoContent.class);

        profileTracker = new ProfileTracker() {
            @Override
            protected void onCurrentProfileChanged(Profile oldProfile, Profile currentProfile) {
                if (mProfileTrackerCallback != null)
                    mProfileTrackerCallback.onCurrentProfileChanged(oldProfile, currentProfile);
            }
        };

    }

    @Override
    public void onDestroy() {
        profileTracker.stopTracking();
    }


    @Override
    public void onPause() {
        // Call the 'deactivateApp' method to log an app event for use in analytics and advertising
        // reporting.  Do so in the onPause methods of the primary Activities that an app may be
        // launched into.
        AppEventsLogger.deactivateApp(mActivity);
    }

    @Override
    public void onResume() {
        // Call the 'activateApp' method to log an app event for use in analytics and advertising
        // reporting.  Do so in the onResume methods of the primary Activities that an app may be
        // launched into.
        AppEventsLogger.activateApp(mActivity);

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        mCallbackManager.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean isLoggedIn() {
        final AccessToken accesstoken = AccessToken.getCurrentAccessToken();
        return !(accesstoken == null || accesstoken.getPermissions().isEmpty());
    }

    @Override
    public boolean hasPermissions(List<String> permissions) {
        AccessToken accessToken = AccessToken.getCurrentAccessToken();
        if (accessToken == null)
            return false;
        for (String permission : permissions) {
            if (!accessToken.getPermissions().contains(permission))
                return false;
        }
        return true;
    }

    @Override
    public void logOut() {
        mLoginManager.logOut();
    }

    //================================================================//
    // Getter & Setter
    //================================================================//
    public void setProfileTrackerCallback(final ProfileTrackerCallback callback) {
        mProfileTrackerCallback = callback;
    }

    @Override
    public void logIn(FacebookCallback<LoginResult> taskCallback) {
        mLoginManager.registerCallback(mCallbackManager, taskCallback);
        mLoginManager.logInWithPublishPermissions(mActivity, Arrays.asList(PERMISSION));
    }

    private void buildShareLink(final Bundle params, final FacebookCallback<Result> taskCallback) {
        Profile profile = Profile.getCurrentProfile();
        ShareLinkContent linkContent = new ShareLinkContent.Builder()
//				.setImageUrl(Uri.parse(params.getString(SHARE_BUNDLE_URL_IMAGE)))
                .setContentTitle(params.getString(SHARE_BUNDLE_TITLE))
                .setContentDescription(params.getString(SHARE_BUNDLE_DESCRIPTION))
                .setContentUrl(Uri.parse(params.getString(SHARE_BUNDLE_URL_CONTENT)))
                .build();

        final ShareDialog shareDialog = new ShareDialog(mActivity);
        shareDialog.registerCallback(mCallbackManager, taskCallback);
        if (canShareLink) {
            shareDialog.show(linkContent);
        } else if (profile != null && hasPermissions(Arrays.asList(PERMISSION))) {
            ShareApi.share(linkContent, taskCallback);
        }
    }

    @Override
    public void newFeed(final Bundle params, final FacebookCallback<Result> taskCallback) {
        if (isLoggedIn()) {
            buildShareLink(params, taskCallback);
        } else {
            mLoginManager.registerCallback(mCallbackManager, new FacebookCallback<LoginResult>() {

                @Override
                public void onSuccess(LoginResult result) {
                    buildShareLink(params, taskCallback);
                }

                @Override
                public void onCancel() {
                    taskCallback.onCancel();
                }

                @Override
                public void onError(FacebookException error) {
                    taskCallback.onError(error);
                }
            });
            mLoginManager.logInWithPublishPermissions(mActivity, Arrays.asList(PERMISSION));
        }
    }

    private void buildSharePhoto(final Bundle params, final Bitmap bitmap, final FacebookCallback<Result> taskCallback) {
        final SharePhoto sharePhoto = new SharePhoto.Builder()
                .setBitmap(bitmap)
                .setCaption(params == null ? "Share Photo" : params.getString(SHARE_BUNDLE_CAPTION))
                .build();
        final ArrayList<SharePhoto> photos = new ArrayList<SharePhoto>();
        photos.add(sharePhoto);

        final SharePhotoContent sharePhotoContent = new SharePhotoContent.Builder()
                .setPhotos(photos)
                .build();
        final ShareDialog shareDialog = new ShareDialog(mActivity);
        shareDialog.registerCallback(mCallbackManager, taskCallback);
        if (canSharePhoto) {
            shareDialog.show(sharePhotoContent);
        } else if (hasPermissions(Arrays.asList(PERMISSION))) {
            ShareApi.share(sharePhotoContent, taskCallback);
        } else {
            Toast.makeText(mActivity, "Can't share photo!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void postPhoto(final Bundle params, final Bitmap bitmap, final FacebookCallback<Result> taskCallback) {
        if (isLoggedIn()) {
            buildSharePhoto(params, bitmap, taskCallback);
        } else {
            mLoginManager.registerCallback(mCallbackManager, new FacebookCallback<LoginResult>() {

                @Override
                public void onSuccess(LoginResult result) {
//					if (canShareLink) {
                    buildSharePhoto(params, bitmap, taskCallback);
//					}
                }

                @Override
                public void onCancel() {
                    taskCallback.onCancel();
                }

                @Override
                public void onError(FacebookException error) {
                    taskCallback.onError(error);
                }
            });
            mLoginManager.logInWithPublishPermissions(mActivity, Arrays.asList(PERMISSION));
        }
    }

    @Override
    public void shareMessenger(String content) {

    }
}