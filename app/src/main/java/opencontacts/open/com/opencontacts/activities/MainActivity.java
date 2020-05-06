package opencontacts.open.com.opencontacts.activities;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.Pair;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import opencontacts.open.com.opencontacts.CardDavSyncActivity;
import opencontacts.open.com.opencontacts.R;
import opencontacts.open.com.opencontacts.actions.ExportMenuItemClickHandler;
import opencontacts.open.com.opencontacts.data.datastore.CallLogDataStore;
import opencontacts.open.com.opencontacts.data.datastore.ContactsDataStore;
import opencontacts.open.com.opencontacts.fragments.CallLogFragment;
import opencontacts.open.com.opencontacts.fragments.ContactsFragment;
import opencontacts.open.com.opencontacts.fragments.DialerFragment;
import opencontacts.open.com.opencontacts.interfaces.SelectableTab;
import opencontacts.open.com.opencontacts.utils.AndroidUtils;
import opencontacts.open.com.opencontacts.utils.DomainUtils;
import opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils;

import static android.view.View.VISIBLE;
import static opencontacts.open.com.opencontacts.utils.AndroidUtils.getThemeAttributeColor;
import static opencontacts.open.com.opencontacts.utils.AndroidUtils.runOnMainDelayed;
import static opencontacts.open.com.opencontacts.utils.AndroidUtils.setColorFilterUsingColor;
import static opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils.getDefaultTab;
import static opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils.markPermissionsAksed;
import static opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils.shouldLaunchDefaultTab;


public class MainActivity extends AppBaseActivity {
    public static final int CONTACTS_TAB_INDEX = 1;
    public static final String INTENT_EXTRA_LONG_CONTACT_ID = "contact_id";
    public static final int DIALER_TAB_INDEX = 2;
    private static final int PREFERENCES_ACTIVITY_RESULT = 773;
    private ViewPager viewPager;
    private SearchView searchView;
    private CallLogFragment callLogFragment;
    private ContactsFragment contactsFragment;
    private DialerFragment dialerFragment;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
//        if(requestCode == DRAW_OVERLAY_PERMISSION_RESULT)//granting permission very fast is resulting in false positive hence delaying this check
//            getMainThreadHandler().postDelayed(this::recreate, 1000);
//
        if(requestCode == PREFERENCES_ACTIVITY_RESULT) recreate();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        recreate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        if(shouldLaunchDefaultTab(this)) gotoDefaultTab();
    }

    private void gotoDefaultTab() {
        // post delayed as view pager is prioritising the fragment launched first as fragment 0 in the list
        // affecting the fragments order etc resulting in cast exception when recreating activity while reusing fragments
        runOnMainDelayed(() -> {
                if(viewPager == null) return;
                viewPager.setCurrentItem(getDefaultTab(this));
            }, 100);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(SharedPreferencesUtils.shouldAskForPermissions(this)){
            AndroidUtils.askForPermissionsIfNotGranted(this);
            View startButton = findViewById(R.id.start_button);
            startButton.setVisibility(VISIBLE);
            startButton.setOnClickListener(x -> this.recreate());
            markPermissionsAksed(this);
            return;
        }
        else {
            setupTabs();
            gotoDefaultTab();
        }
        markPermissionsAksed(this);
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Warning")
                .setMessage("Do not use this test app, this can leak all your contacts to others apps")
                .setPositiveButton("I know what I am doing", null)
                .setCancelable(true)
                .show();
    }

    @Override
    int getLayoutResource() {
        return R.layout.activity_tabbed;
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_menu, menu);
        setMenuItemsListeners(menu);
        return super.onCreateOptionsMenu(menu);
    }

    private void setMenuItemsListeners(Menu menu) {
        menu.findItem(R.id.button_new).setOnMenuItemClickListener(item -> {
            Intent addContact = new Intent(MainActivity.this, EditContactActivity.class);
            addContact.putExtra(EditContactActivity.INTENT_EXTRA_BOOLEAN_ADD_NEW_CONTACT, true);
            startActivity(addContact);
            return false;
        });
        MenuItem searchItem = menu.findItem(R.id.action_search);
        searchView = (SearchView) searchItem.getActionView();
        searchView.setOnSearchClickListener(v -> {
            viewPager.setCurrentItem(CONTACTS_TAB_INDEX);
            searchView.requestFocus();
        });
        menu.findItem(R.id.action_sync).setOnMenuItemClickListener(x -> {
            startActivity(new Intent(this, CardDavSyncActivity.class));
            return true;
        });

        if(contactsFragment != null)
            contactsFragment.configureSearchInMenu(searchView);
        menu.findItem(R.id.action_export).setOnMenuItemClickListener(new ExportMenuItemClickHandler(this));
        menu.findItem(R.id.action_about).setOnMenuItemClickListener(item -> {
            startActivity(new Intent(MainActivity.this, AboutActivity.class));
            return true;
        });
        menu.findItem(R.id.action_help).setOnMenuItemClickListener(item -> {
            startActivity(new Intent(MainActivity.this, HelpActivity.class));
            return true;
        });

        menu.findItem(R.id.action_preferences).setOnMenuItemClickListener(item -> {
            startActivityForResult(new Intent(MainActivity.this, PreferencesActivity.class), PREFERENCES_ACTIVITY_RESULT);
            return true;
        });

        menu.findItem(R.id.action_resync).setOnMenuItemClickListener(item -> {
            CallLogDataStore.updateCallLogAsyncForAllContacts(MainActivity.this);
            return true;
        });
        menu.findItem(R.id.action_whats_new).setOnMenuItemClickListener(item -> {
            AndroidUtils.goToUrl(getString(R.string.gitlab_repo_tags_url), MainActivity.this);
           return true;
        });
        menu.findItem(R.id.action_export_call_log).setOnMenuItemClickListener(item -> {
            Toast.makeText(this, R.string.started_exporting_call_log, Toast.LENGTH_SHORT).show();
            try {
                DomainUtils.exportCallLog(this);
                Toast.makeText(this, R.string.exported_call_log_successfully, Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                Toast.makeText(this, R.string.failed_exporting_call_log, Toast.LENGTH_LONG).show();
            }
            return true;
        });
        menu.findItem(R.id.action_delete_all_contacts).setOnMenuItemClickListener(item -> {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.delete_all_contacts_question)
                    .setPositiveButton(R.string.okay,
                            (dialogInterface, i) -> ContactsDataStore.deleteAllContacts(MainActivity.this))
                    .show();
            return true;
        });
    }

    private void refresh() {
        CallLogDataStore.loadRecentCallLogEntriesAsync(MainActivity.this);
    }

    private void setupTabs() {
        viewPager = findViewById(R.id.view_pager);
        List<Fragment> fragmentsList = getSupportFragmentManager().getFragments();
        if(!fragmentsList.isEmpty()){
            callLogFragment = (CallLogFragment) fragmentsList.get(0);
            contactsFragment = (ContactsFragment) fragmentsList.get(1);
            dialerFragment = (DialerFragment) fragmentsList.get(2);
        }
        else{
            callLogFragment = new CallLogFragment();
            contactsFragment = new ContactsFragment();
            dialerFragment = new DialerFragment();
        }
        callLogFragment.setEditNumberBeforeCallHandler(number -> {
            dialerFragment.setNumber(number);
            viewPager.setCurrentItem(DIALER_TAB_INDEX);
        });
        final List<SelectableTab> fragments = new ArrayList<>(Arrays.asList(callLogFragment, contactsFragment, dialerFragment));
        final List<String> tabTitles = Arrays.asList(getString(R.string.calllog), getString(R.string.contacts), "");

        FragmentPagerAdapter fragmentPagerAdapter = new FragmentPagerAdapter(getSupportFragmentManager()) {
            @Override
            public int getCount() {
                return 3;
            }

            @Override
            public CharSequence getPageTitle(int position) {
                return tabTitles.get(position);
            }

            @Override
            public Fragment getItem(int position) {
                return (Fragment) fragments.get(position);
            }
        };
        viewPager.setAdapter(fragmentPagerAdapter);
        viewPager.setOffscreenPageLimit(3); //crazy shit with viewPager in case used with tablayout

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(viewPager);

        Pair<Drawable, Drawable> dialerTabDrawables = getDialerTabDrawables();
        tabLayout.getTabAt(DIALER_TAB_INDEX).setIcon(dialerTabDrawables.first);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                fragments.get(tab.getPosition()).onSelect();
                if(tab.getPosition() == DIALER_TAB_INDEX){
                    tab.setIcon(dialerTabDrawables.second);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                fragments.get(tab.getPosition()).onUnSelect();
                if(tab.getPosition() == DIALER_TAB_INDEX){
                    tab.setIcon(dialerTabDrawables.first);
                }
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
    }

    private Pair<Drawable, Drawable> getDialerTabDrawables(){
        int tabSelectedColor = getThemeAttributeColor(android.R.attr.textColorPrimary, MainActivity.this);
        int tabUnSelectedColor = getThemeAttributeColor(android.R.attr.textColorSecondary, MainActivity.this);
        Drawable dialpadIconOnUnSelect = ContextCompat.getDrawable(this, R.drawable.dial_pad).mutate();
        Drawable dialpadIconOnSelect = ContextCompat.getDrawable(this, R.drawable.dial_pad).mutate();
        setColorFilterUsingColor(dialpadIconOnSelect, tabSelectedColor);
        setColorFilterUsingColor(dialpadIconOnUnSelect, tabUnSelectedColor);
        return Pair.create(dialpadIconOnUnSelect, dialpadIconOnSelect);
    }
    public void collapseSearchView(){
        searchView.onActionViewCollapsed();
    }
}