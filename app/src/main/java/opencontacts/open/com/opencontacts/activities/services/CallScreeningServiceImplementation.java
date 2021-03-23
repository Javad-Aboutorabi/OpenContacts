package opencontacts.open.com.opencontacts.activities.services;

import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.telecom.Call;
import android.telecom.CallScreeningService;

import opencontacts.open.com.opencontacts.data.datastore.ContactsDataStore;
import opencontacts.open.com.opencontacts.orm.Contact;

import static android.telecom.Call.Details.DIRECTION_OUTGOING;

@RequiresApi(api = Build.VERSION_CODES.N)
public class CallScreeningServiceImplementation extends CallScreeningService {
    public static CallResponse getRejectResponse() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSilenceCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build();
        }
        return new CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build();
    }

    CallResponse reject = getRejectResponse();

    CallResponse allow = new CallResponse.Builder()
            .build();

    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        if (callDetails.getCallDirection() == DIRECTION_OUTGOING) {
            respondToCall(callDetails, allow);
            return;
        }
        String callingPhonenumber = callDetails.getHandle().getSchemeSpecificPart();
        Contact probableContact = ContactsDataStore.getContact(callingPhonenumber);
        if (probableContact == null) respondToCall(callDetails, reject);
        else respondToCall(callDetails, allow);
    }
}
