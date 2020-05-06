package opencontacts.open.com.opencontacts.data.datastore;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import com.github.underscore.U;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ezvcard.VCard;
import opencontacts.open.com.opencontacts.R;
import opencontacts.open.com.opencontacts.domain.Contact;
import opencontacts.open.com.opencontacts.interfaces.DataStoreChangeListener;
import opencontacts.open.com.opencontacts.orm.CallLogEntry;
import opencontacts.open.com.opencontacts.orm.Favorite;
import opencontacts.open.com.opencontacts.orm.PhoneNumber;
import opencontacts.open.com.opencontacts.orm.VCardData;

import static opencontacts.open.com.opencontacts.interfaces.DataStoreChangeListener.ADDITION;
import static opencontacts.open.com.opencontacts.interfaces.DataStoreChangeListener.DELETION;
import static opencontacts.open.com.opencontacts.interfaces.DataStoreChangeListener.REFRESH;
import static opencontacts.open.com.opencontacts.interfaces.DataStoreChangeListener.UPDATION;
import static opencontacts.open.com.opencontacts.utils.AndroidUtils.processAsync;
import static opencontacts.open.com.opencontacts.utils.AndroidUtils.toastFromNonUIThread;

public class ContactsDataStore {
    private static List<Contact> contacts = null;
    private static final List<DataStoreChangeListener<Contact>> dataChangeListeners = Collections.synchronizedList(new ArrayList<>(3));
    private static List<Contact> favorites = new ArrayList<>(0);
    private static boolean initialized = false;
    private static boolean contactsLoaded = false;
    private static List<PhoneNumber> phoneNumbers = null;

    public static List<Contact> getAllContacts() {
        if (contacts == null) {
            refreshStoreAsync();
            return new ArrayList<>(0);
        }
        return new ArrayList<>(contacts);
    }

    public static List<PhoneNumber> cautiouslyGetAllPhoneNumberEntries () {
        if(phoneNumbers == null) phoneNumbers = PhoneNumber.listAll(PhoneNumber.class);
        return phoneNumbers;
    }

    // dont use this method. Always try async means
    public static List<Contact> cautiouslyGetAllContactsFromDB() {
        contacts = ContactsDBHelper.getAllContactsFromDB();
        notifyListenersAsync(REFRESH, null);
        contactsLoaded = true;
        return getAllContacts();
    }

    public static void addContact(VCard vCard, Context context) {
        opencontacts.open.com.opencontacts.orm.Contact newContactWithDatabaseId = ContactsDBHelper.addContact(vCard, context);
        Contact addedDomainContact = ContactsDBHelper.getContact(newContactWithDatabaseId.getId());
        contacts.add(addedDomainContact);
        notifyListenersAsync(ADDITION, addedDomainContact);
        CallLogDataStore.updateCallLogAsyncForNewContact(addedDomainContact);
    }

    public static void removeContact(Contact contact) {
        if (contacts.remove(contact)) {
            ContactsDBHelper.deleteContactInDB(contact.id);
            removeFavorite(contact);
            notifyListenersAsync(DELETION, contact);
        }
    }

    public static void removeContact(long contactId) {
        removeContact(getContactWithId(contactId));
    }

    public static void updateContact(long contactId, String primaryNumber, VCard vCard, Context context) {
        ContactsDBHelper.updateContactInDBWith(contactId, primaryNumber, vCard, context);
        reloadContact(contactId);
        CallLogDataStore.updateCallLogAsyncForNewContact(getContactWithId(contactId));
    }

    private static void reloadContact(long contactId) {
        int indexOfContact = contacts.indexOf(new Contact(contactId));
        if (indexOfContact == -1)
            return;
        Contact contactFromDB = ContactsDBHelper.getContact(contactId);
        contacts.set(indexOfContact, contactFromDB);
        notifyListenersAsync(UPDATION, contactFromDB);
    }

    public static void addDataChangeListener(DataStoreChangeListener<Contact> changeListener) {
        synchronized (dataChangeListeners){
            dataChangeListeners.add(changeListener);
        }
    }

    public static void removeDataChangeListener(DataStoreChangeListener<Contact> changeListener) {
        processAsync(() -> { //for those listeners who want to deregister inside the registered listener callback
            synchronized (dataChangeListeners){
                dataChangeListeners.remove(changeListener);
            }
        });
    }

    public static opencontacts.open.com.opencontacts.orm.Contact getContact(String phoneNumber) {
        return ContactsDBHelper.getContactFromDB(phoneNumber);
    }

    public static Contact cautiouslyGetContactFromDatabase(long contactId) throws Exception{
        return U.checkNotNull(ContactsDBHelper.getContact(contactId));
    }

    public static Contact getContactWithId(long contactId) {
        if (contactId == -1 || contacts == null)
            return null;
        int indexOfContact = contacts.indexOf(new Contact(contactId));
        if (indexOfContact == -1)
            return null;
        return contacts.get(indexOfContact);
    }

    public static void updateContactsAccessedDateAsync(final List<CallLogEntry> newCallLogEntries) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                for (CallLogEntry callLogEntry : newCallLogEntries) {
                    long contactId = callLogEntry.getContactId();
                    if(getContactWithId(contactId) == null)
                        continue;
                    ContactsDBHelper.updateLastAccessed(contactId, callLogEntry.getDate());
                }
                refreshStoreAsync();
                return null;
            }
        }.execute();
    }

    public static void togglePrimaryNumber(String mobileNumber, long contactId) {
        ContactsDBHelper.togglePrimaryNumber(mobileNumber, getContactWithId(contactId));
        reloadContact(contactId);
    }

    public static void refreshStoreAsync() {
        processAsync(ContactsDataStore::refreshStore);
    }

    public static boolean hasContactsLoaded() {
        return contactsLoaded;
    }

    private static void refreshStore() {
        contacts = ContactsDBHelper.getAllContactsFromDB();
        contactsLoaded = true;
        notifyListeners(REFRESH, null);
    }

    private static void notifyListenersAsync(final int type, final Contact contact){
        if(dataChangeListeners.isEmpty())
            return;
        processAsync(() -> notifyListeners(type, contact));
    }

    private static void notifyListeners(int type, Contact contact) {
        if(dataChangeListeners.isEmpty())
            return;
        synchronized (dataChangeListeners){
            U.forEach(dataChangeListeners, listener -> {
                if(listener == null) return;
                if(type == ADDITION)
                    listener.onAdd(contact);
                else if(type == DELETION)
                    listener.onRemove(contact);
                else if(type == UPDATION)
                    listener.onUpdate(contact);
                else if (type == REFRESH)
                    listener.onStoreRefreshed();
            });
        }
    }

    public static void deleteAllContacts(Context context) {
        processAsync(() -> {
            ContactsDBHelper.deleteAllContacts();
            refreshStore();
            toastFromNonUIThread(R.string.deleted_all_contacts, Toast.LENGTH_LONG, context);
        });
    }

    public static VCardData getVCardData(long contactId){
        return ContactsDBHelper.getVCard(contactId);
    }

    public static void init() {
        if (!initialized) refreshStoreAsync();
        initialized = true;
    }

    public static void updateFavoritesList(){
        favorites = U.chain(Favorite.listAll(Favorite.class))
                .map(favoriteFromDB -> favoriteFromDB.contact.getId())
                .map(ContactsDataStore::getContactWithId)
                .filterFalse(U::isNull)
                .value();
    }

    public static List<Contact> getFavorites(){
        if(favorites.size() != 0 || Favorite.count(Favorite.class) == 0)
            return favorites;
        updateFavoritesList();
        return favorites;
    }

    public static void addFavorite(Contact contact) {
        if(getFavorites().contains(contact)) return;
        new Favorite(ContactsDBHelper.getDBContactWithId(contact.id)).save();
        favorites.add(contact);
        notifyListenersAsync(REFRESH, null);
    }

    public static void removeFavorite(Contact contact) {
        List<Favorite> favoriteContactQueryResults = Favorite.find(Favorite.class, "contact = ?", contact.id + "");
        Favorite.deleteInTx(favoriteContactQueryResults);
        favorites.remove(contact);
        notifyListenersAsync(REFRESH, null);
    }

    public static boolean isFavorite(Contact contact){
        return getFavorites().contains(contact);
    }
}