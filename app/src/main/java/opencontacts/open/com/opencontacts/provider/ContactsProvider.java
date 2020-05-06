package opencontacts.open.com.opencontacts.provider;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.github.underscore.U;

import java.util.List;

import opencontacts.open.com.opencontacts.data.datastore.ContactsDataStore;
import opencontacts.open.com.opencontacts.domain.Contact;
import opencontacts.open.com.opencontacts.orm.PhoneNumber;

import static android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE;
import static opencontacts.open.com.opencontacts.data.datastore.ContactsDataStore.cautiouslyGetAllContactsFromDB;
import static opencontacts.open.com.opencontacts.data.datastore.ContactsDataStore.cautiouslyGetAllPhoneNumberEntries;

public class ContactsProvider extends ContentProvider {
    public static final String PHONENUMBER_VALUE_TYPE_TEXT = "TEXT";
    public static String accountType = "OpenContacts";
    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        String[] columnNames = new String[]{
                Phone._ID,
                Phone.LOOKUP_KEY,
                Phone.ACCOUNT_TYPE_AND_DATA_SET,
                Phone.NUMBER,
                Phone.TYPE,
                Phone.LABEL,
                Phone.DISPLAY_NAME,
                Phone.PHOTO_URI,
                Phone.STARRED,
                Phone.CONTACT_LAST_UPDATED_TIMESTAMP};
        MatrixCursor matrixCursor = new MatrixCursor(columnNames);
        List<PhoneNumber> allPhoneNumbers = cautiouslyGetAllPhoneNumberEntries();
        U.chain(allPhoneNumbers)
                .map(this::mapPhoneNumberToContactRow)
                .forEach(matrixCursor::addRow);
        return matrixCursor;
    }

    @NonNull
    private Object[] mapPhoneNumberToContactRow(PhoneNumber phoneNumber) {
        Object[] contactRow = new Object[10];
        contactRow[0] = String.valueOf(phoneNumber.getId());
        contactRow[1] = String.valueOf(phoneNumber.getId());
        contactRow[2] = accountType;
        contactRow[3] = phoneNumber.phoneNumber;
        contactRow[4] = TYPE_MOBILE;
        contactRow[5] = PHONENUMBER_VALUE_TYPE_TEXT;
        contactRow[6] = phoneNumber.contact.getFullName();
        contactRow[7] = null;
        contactRow[8] = (int) 0;
        contactRow[9] = 0;
        return contactRow;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
