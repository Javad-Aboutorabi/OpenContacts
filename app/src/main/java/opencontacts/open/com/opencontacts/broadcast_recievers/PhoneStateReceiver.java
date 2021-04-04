package opencontacts.open.com.opencontacts.broadcast_recievers;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.provider.CallLog;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Random;

import opencontacts.open.com.opencontacts.data.datastore.CallLogDataStore;
import opencontacts.open.com.opencontacts.R;
import opencontacts.open.com.opencontacts.activities.MainActivity;
import opencontacts.open.com.opencontacts.data.datastore.ContactsDataStore;
import opencontacts.open.com.opencontacts.orm.CallLogEntry;
import opencontacts.open.com.opencontacts.orm.Contact;
import opencontacts.open.com.opencontacts.utils.AndroidUtils;

import static android.view.WindowManager.LayoutParams.TYPE_PHONE;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
import static opencontacts.open.com.opencontacts.OpenContactsApplication.MISSED_CALLS_CHANEL_ID;
import static opencontacts.open.com.opencontacts.utils.AndroidUtils.isScreenLocked;
import static opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils.getCallerIdLocationOnScreen;
import static opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils.saveCallerIdLocationOnScreen;
import static opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils.shouldAutoCancelMissedCallNotification;

/**
 * Created by sultanm on 7/30/17.
 */
public class PhoneStateReceiver extends BroadcastReceiver {
    private static View drawOverIncomingCallLayout = null;
    private static boolean isCallRecieved;
    private static Contact callingContact;
    private static String incomingNumber;
    /*
        Lolipop has a problem of raising these events multiple times leading to multiple
        drawing of caller id, multiple notifications. Ahhhhhhhhhh!!!!!!
     */
    private static String prevState;
    private static long prevStateTimeStamp;


    @Override
    public void onReceive(final Context context, Intent intent) {
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if(state.equals(TelephonyManager.EXTRA_STATE_RINGING)){
            isCallRecieved = false;
            incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            if(incomingNumber == null) return; //in pie, two intents are launched one with number and other with not
            callingContact = ContactsDataStore.getContact(incomingNumber);
            if(callingContact == null)
                callingContact = new Contact(context.getString(R.string.unknown), incomingNumber);
            drawCallerID(context, callingContact);
        }
        else if(state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)){
            removeCallerIdDrawing(context);
            isCallRecieved = true;
        }
        else if(state.equals(TelephonyManager.EXTRA_STATE_IDLE)){
            removeCallerIdDrawing(context);
            if(isCallRecieved || state.equals(prevState))
                return;
            // give android some time to write call log
            new Handler().postDelayed(() -> notifyAboutMissedCall(context), 3000);
        }
        prevState = state;
        prevStateTimeStamp = System.currentTimeMillis();
    }

    private void notifyAboutMissedCall(Context context) {
        if(callingContact == null) return; //#98 issue with marshmallow.
        try{
            CallLogEntry callLogEntry =  CallLogDataStore.getMostRecentCallLogEntry(context);
            if(callLogEntry == null || !callLogEntry.getCallType().equals(String.valueOf(CallLog.Calls.MISSED_TYPE)))
                return;
        }
        catch (Exception e){}
        PendingIntent pendingIntentToLaunchApp = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pendingIntentToCall = PendingIntent.getActivity(context, 0, AndroidUtils.getIntentToCall(incomingNumber, context), PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pendingIntentToMessage = PendingIntent.getActivity(context, 0, AndroidUtils.getIntentToMessage(incomingNumber), PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(context, MISSED_CALLS_CHANEL_ID)
                        .setSmallIcon(R.drawable.ic_phone_missed_black_24dp)
                        .setContentTitle(context.getString(R.string.missed_call))
                        .setAutoCancel(shouldAutoCancelMissedCallNotification(context))
                        .setTicker(context.getString(R.string.missed_call_from, callingContact.firstName, callingContact.lastName))
                        .setContentText(callingContact.firstName + " " + callingContact.lastName)
                        .addAction(R.drawable.ic_call_black_24dp, context.getString(R.string.call), pendingIntentToCall)
                        .addAction(R.drawable.ic_chat_black_24dp, context.getString(R.string.message), pendingIntentToMessage)
                        .setContentIntent(pendingIntentToLaunchApp);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(new Random().nextInt(), mBuilder.build());
    }

    private void drawCallerID(Context context, Contact callingContact) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) return;
        if(drawOverIncomingCallLayout != null) return;
        final WindowManager windowManager = (WindowManager) context.getSystemService(context.WINDOW_SERVICE);
        LayoutInflater layoutinflater = LayoutInflater.from(context);
        drawOverIncomingCallLayout = layoutinflater.inflate(R.layout.draw_over_incoming_call, null);
        TextView contactName = drawOverIncomingCallLayout.findViewById(R.id.name_of_contact);
        View close = drawOverIncomingCallLayout.findViewById(R.id.close);
        close.setOnClickListener(v -> removeCallerIdDrawing(context));
        contactName.setText(context.getString(R.string.caller_id_text, callingContact.getFullName()));
        int typeOfWindow;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            typeOfWindow = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        else
            typeOfWindow = isScreenLocked(context) ? TYPE_SYSTEM_OVERLAY : TYPE_PHONE;

        final WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                typeOfWindow,
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                PixelFormat.TRANSLUCENT);

        Point callerIdLocationOnScreen = getCallerIdLocationOnScreen(context);
        layoutParams.x = callerIdLocationOnScreen.x;
        layoutParams.y = callerIdLocationOnScreen.y;
        layoutParams.verticalWeight = 0;
        layoutParams.horizontalWeight = 0;
        layoutParams.horizontalMargin = 0;

        drawOverIncomingCallLayout.setOnTouchListener(new View.OnTouchListener() {
            private float initialTouchX = -1;
            private float initialTouchY = -1;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_MOVE){
                    layoutParams.x = (int) (layoutParams.x + (event.getX() - initialTouchX));
                    layoutParams.y = (int) (layoutParams.y + (event.getY() - initialTouchY));
                    windowManager.updateViewLayout(drawOverIncomingCallLayout, layoutParams);
                    return true;
                }
                if(MotionEvent.ACTION_DOWN == event.getAction()) {
                    initialTouchX = event.getX();
                    initialTouchY = event.getY();
                    return true;
                }
                return false;
            }
        });
        windowManager.addView(drawOverIncomingCallLayout, layoutParams);
    }

    private void removeCallerIdDrawing(Context context) {
        if(drawOverIncomingCallLayout == null)
            return;
        WindowManager windowManager = (WindowManager) context.getSystemService(context.WINDOW_SERVICE);
        WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) drawOverIncomingCallLayout.getLayoutParams();
        saveCallerIdLocationOnScreen(layoutParams.x, layoutParams.y, context);
        windowManager.removeView(drawOverIncomingCallLayout);
        drawOverIncomingCallLayout = null;
    }
}
