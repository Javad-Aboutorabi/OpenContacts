package opencontacts.open.com.opencontacts.data.datastore;

import android.content.Context;
import android.support.v4.util.ArrayMap;

import com.github.underscore.U;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import opencontacts.open.com.opencontacts.domain.Contact;
import opencontacts.open.com.opencontacts.interfaces.DataStoreChangeListener;
import opencontacts.open.com.opencontacts.orm.CallLogEntry;
import opencontacts.open.com.opencontacts.orm.PhoneNumber;

import static opencontacts.open.com.opencontacts.utils.AndroidUtils.processAsync;
import static opencontacts.open.com.opencontacts.utils.DomainUtils.getAllNumericPhoneNumber;
import static opencontacts.open.com.opencontacts.utils.DomainUtils.getSearchablePhoneNumber;

public class CallLogDataStore {
    public static final int CALL_LOG_ENTRIES_CHUNK_SIZE = 100;
    private static CallLogDBHelper callLogDBHelper = new CallLogDBHelper();
    private static List<CallLogEntry> callLogEntries = new ArrayList<>(0);
    private static List<DataStoreChangeListener<CallLogEntry>> dataChangeListeners = new ArrayList<>(3);

    public static synchronized void loadRecentCallLogEntriesAsync(Context context) {
        processAsync(() -> loadRecentCallLogEntries(context));
    }

    public static synchronized void loadRecentCallLogEntries(Context context) {
        final List<CallLogEntry> recentCallLogEntries = callLogDBHelper.loadRecentCallLogEntriesIntoDB(context);
        if(recentCallLogEntries.isEmpty())
            return;
        ContactsDataStore.updateContactsAccessedDateAsync(recentCallLogEntries);
        addRecentCallLogEntriesToStore(recentCallLogEntries);
    }

    private static void addRecentCallLogEntriesToStore(final List<CallLogEntry> recentCallLogEntries) {
        if(recentCallLogEntries.size() > 1){
            refreshStore();
        }
        else if(recentCallLogEntries.size() == 1){
            CallLogEntry callLogEntry = recentCallLogEntries.get(0);
            callLogEntries.add(0, callLogEntry);
            processAsync(() -> {
                for(DataStoreChangeListener<CallLogEntry> dataStoreChangeListener: dataChangeListeners){
                    dataStoreChangeListener.onAdd(callLogEntry);
                }
            });
        }
    }

    private synchronized static void refreshStore() {
        callLogEntries = CallLogDBHelper.getRecentCallLogEntriesFromDB();
        if(!callLogEntries.isEmpty()) {
            notifyRefreshStore();
        }
    }

    private static void notifyRefreshStore() {
        for (DataStoreChangeListener<CallLogEntry> dataStoreChangeListener : dataChangeListeners) {
            dataStoreChangeListener.onStoreRefreshed();
        }
    }

    public static CallLogEntry getMostRecentCallLogEntry(Context context) {
        loadRecentCallLogEntries(context);
        return callLogEntries.isEmpty() ? null : callLogEntries.get(0);
    }

    public static List<CallLogEntry> getRecentCallLogEntries(Context context){
        if(callLogEntries.isEmpty()){
            processAsync(CallLogDataStore::refreshStore);
            return new ArrayList<>(0);
        }
        return new ArrayList<>(callLogEntries);
    }

    public static void addDataChangeListener(DataStoreChangeListener<CallLogEntry> changeListener) {
        dataChangeListeners.add(changeListener);
    }

    public static void removeDataChangeListener(DataStoreChangeListener<CallLogEntry> changeListener) {
        dataChangeListeners.remove(changeListener);
    }

    public static void updateCallLogAsyncForNewContact(final Contact newContact){
        processAsync(new Runnable() {
            @Override
            public void run() {
                List<CallLogEntry> callLogEntriesToWorkWith = getCallLogEntriesToWorkWith();
                if(callLogEntriesToWorkWith.isEmpty())
                    return;
                int numberOfEntriesUpdated = 0;
                for(PhoneNumber phoneNumber : newContact.phoneNumbers) {
                    String searchablePhoneNumber = getSearchablePhoneNumber(phoneNumber.phoneNumber);
                    if (searchablePhoneNumber == null)
                        continue;
                    for (CallLogEntry callLogEntry : callLogEntriesToWorkWith) {
                        if (callLogEntry.getContactId() != -1)
                            continue;
                        String allNumericPhoneNumberOfCallLogEntry = getAllNumericPhoneNumber(callLogEntry.getPhoneNumber());
                        if(allNumericPhoneNumberOfCallLogEntry.contains(searchablePhoneNumber)){
                            callLogEntry.setContactId(newContact.id);
                            callLogEntry.setName(newContact.name);
                            callLogEntry.save();
                            numberOfEntriesUpdated ++;
                            break;
                        }
                    }
                }
                if(numberOfEntriesUpdated == 0)
                    return;
                notifyRefreshStore();
            }

            private List<CallLogEntry> getCallLogEntriesToWorkWith() {
                return callLogEntries.isEmpty() ? CallLogDBHelper.getRecentCallLogEntriesFromDB() : callLogEntries;
            }
        });
    }

    public static void updateCallLogAsyncForAllContacts(final Context context){
        processAsync(() -> updateCallLogForAllContacts(context));
    }

    public static void updateCallLogForAllContacts(Context context) {
        if(callLogEntries == null)
            callLogEntries = getRecentCallLogEntries(context);
        int numberOfEntriesUpdated = 0;
        for(CallLogEntry callLogEntry : callLogEntries){
            if(callLogEntry.getContactId() != -1)
                continue;
            opencontacts.open.com.opencontacts.orm.Contact contactFromDB = ContactsDBHelper.getContactFromDB(callLogEntry.getPhoneNumber());
            if(contactFromDB == null)
                continue;
            callLogEntry.setName(contactFromDB.firstName + " " + contactFromDB.lastName);
            callLogEntry.setContactId(contactFromDB.getId());
            callLogEntry.save();
            numberOfEntriesUpdated ++;
        }
        if(numberOfEntriesUpdated == 0)
            return;
        notifyRefreshStore();
    }

    public static void delete(Long id) {
        boolean hasBeenDeleted = CallLogDBHelper.delete(id);
        if(!hasBeenDeleted)
            return;
        for(CallLogEntry callLogEntryToBeRemoved : callLogEntries){
            if(!callLogEntryToBeRemoved.getId().equals(id))
                continue;
            callLogEntries.remove(callLogEntryToBeRemoved);
            processAsync(() -> {
                for(DataStoreChangeListener<CallLogEntry> dataStoreChangeListener: dataChangeListeners){
                    dataStoreChangeListener.onRemove(callLogEntryToBeRemoved);
                }
            });
            break;
        }
    }

    public static void init(Context context) {
        processAsync(() -> {
            refreshStore();
            loadRecentCallLogEntries(context);
        });
    }

    public static void removeAllContactsLinking() {
        CallLogDBHelper.removeAllContactsLinking();
        CallLogDataStore.refreshStoreAsync();
    }

    private static void refreshStoreAsync() {
        processAsync(CallLogDataStore::refreshStore);
    }

    public static Collection<CallLogEntry> getUnLabelledCallLogEntriesMatching(String number) {
        ArrayMap<String, CallLogEntry> matchedEntries = new ArrayMap<>();
        U.forEach(callLogEntries, entry -> {
            if(entry.name != null) return;
            String phoneNumber = entry.getPhoneNumber();
            String searchablePhoneNumber = getSearchablePhoneNumber(phoneNumber);
            if(searchablePhoneNumber == null || !searchablePhoneNumber.contains(number)) return;
            matchedEntries.put(phoneNumber, entry);
        });
        return matchedEntries.values();
    }

    public static void loadNextChunkOfCallLogEntries() {
        processAsync(() -> {
            callLogEntries = CallLogDBHelper.getCallLogEntriesFromDB(callLogEntries.size() + CALL_LOG_ENTRIES_CHUNK_SIZE);
            notifyRefreshStore();
        });
    }
}