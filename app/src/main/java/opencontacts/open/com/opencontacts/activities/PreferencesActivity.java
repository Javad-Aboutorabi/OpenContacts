package opencontacts.open.com.opencontacts.activities;


import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.view.ContextThemeWrapper;
import android.support.v7.widget.AppCompatEditText;
import android.text.InputType;
import android.util.TypedValue;
import android.widget.Toast;

import com.github.underscore.Supplier;
import com.github.underscore.U;

import java.util.Arrays;
import java.util.HashMap;

import open.fontscaling.FontScalePreferenceHandler;
import opencontacts.open.com.opencontacts.R;
import opencontacts.open.com.opencontacts.components.TintedDrawablesStore;

import static android.app.role.RoleManager.ROLE_CALL_SCREENING;
import static android.widget.Toast.LENGTH_SHORT;
import static open.fontscaling.SharePrefUtil.TEXT_SIZE_SCALING_SHARED_PREF_KEY;
import static opencontacts.open.com.opencontacts.utils.AndroidUtils.isWhatsappInstalled;
import static opencontacts.open.com.opencontacts.utils.AndroidUtils.showAlert;
import static opencontacts.open.com.opencontacts.utils.PhoneCallUtils.getSimNames;
import static opencontacts.open.com.opencontacts.utils.PhoneCallUtils.hasMultipleSims;
import static opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils.COMMON_SHARED_PREFS_FILE_NAME;
import static opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils.DEFAULT_SIM_SELECTION_ALWAYS_ASK;
import static opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils.DEFAULT_SIM_SELECTION_SYSTEM_DEFAULT;
import static opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils.ENABLE_CALL_FILTERING_SHARED_PREF_KEY;
import static opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils.IS_DARK_THEME_ACTIVE_PREFERENCES_KEY;
import static opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils.SHOULD_USE_SYSTEM_PHONE_APP;
import static opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils.SIM_PREFERENCE_SHARED_PREF_KEY;
import static opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils.T9_SEARCH_ENABLED_SHARED_PREF_KEY;
import static opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils.WHATSAPP_INTEGRATION_ENABLED_PREFERENCE_KEY;
import static opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils.disableWhatsappIntegration;
import static opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils.enableCallFiltering;
import static opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils.enableWhatsappIntegration;
import static opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils.getDefaultWhatsAppCountryCode;
import static opencontacts.open.com.opencontacts.utils.SharedPreferencesUtils.getPreferredSim;

public class PreferencesActivity extends AppBaseActivity {

    public static final String PREFERENCE_FRAGMENT_TRANSACTION_TAG = "preference";
    public static final int REQUEST_TO_BECOMING_CALL_SCREENER = 22557;
    public static final String GENERAL_PREF_GROUP = "General";
    public static final String CALL_FILTERING_PREF_GROUP = "CallFiltering";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FragmentManager supportFragmentManager = getSupportFragmentManager();
        if(!supportFragmentManager.getFragments().isEmpty()) return;
        supportFragmentManager
                .beginTransaction()
                .add(R.id.fragment_container, new PreferencesFragment(), PREFERENCE_FRAGMENT_TRANSACTION_TAG)
                .commit();
    }

    @Override
    int getLayoutResource() {
        return R.layout.activity_preferences;
    }


    public static class PreferencesFragment extends PreferenceFragmentCompat {
        Activity activity = null;

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            activity = getActivity();
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            getPreferenceManager().setSharedPreferencesName(COMMON_SHARED_PREFS_FILE_NAME);
            addPreferencesFromResource(R.xml.app_preferences);
            addConditionalPreferences();
            initFontScalePreference();
        }

        private void addConditionalPreferences() {
            if (hasMultipleSims(getContext())) addSimPreference();
            enablePreferenceIf(SHOULD_USE_SYSTEM_PHONE_APP, () -> android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N);
            enablePreferenceIf(CALL_FILTERING_PREF_GROUP, () -> android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q);
            handlePreferenceUpdates();
        }

        private void initFontScalePreference() {
            Preference fontScalPreference = ((PreferenceCategory) getPreferenceScreen().findPreference(GENERAL_PREF_GROUP)).findPreference(TEXT_SIZE_SCALING_SHARED_PREF_KEY);
            fontScalPreference.setOnPreferenceClickListener(preference -> {
                new FontScalePreferenceHandler(activity).open();
                return true;
            });
        }

        private void enablePreferenceIf(String preferenceKey, Supplier<Boolean> shouldEnable) {
            if (shouldEnable.get()) findPreference(preferenceKey).setEnabled(true);
        }

        private boolean hasNoPreferredSim() {
            return U.contains(Arrays.asList(DEFAULT_SIM_SELECTION_SYSTEM_DEFAULT, DEFAULT_SIM_SELECTION_ALWAYS_ASK), getPreferredSim(getContext()));
        }

        private void addSimPreference() {
            ContextThemeWrapper contextThemeWrapper = getContextThemeWrapper();// crazy android hack to get theme wrapped content. Else the dynamically created preferences are failing to create.

            ListPreference listPreference = new ListPreference(contextThemeWrapper);
            String[] simSelectionTitles = getResources().getStringArray(R.array.sim_selection);
            String[] simNames = getSimNames(getContext());
            simSelectionTitles[2] = simNames[0];
            simSelectionTitles[3] = simNames[1];
            String simSelectionSummary = hasNoPreferredSim() ? "%s" : simNames[Integer.valueOf(getPreferredSim(getContext()))];
            listPreference.setEntries(simSelectionTitles);
            listPreference.setTitle(R.string.default_sim_calls_preference_title);
            listPreference.setSummary(simSelectionSummary);
            listPreference.setEntryValues(R.array.sim_selection_values);
            listPreference.setDefaultValue(DEFAULT_SIM_SELECTION_SYSTEM_DEFAULT);
            listPreference.setKey(SIM_PREFERENCE_SHARED_PREF_KEY);
            ((PreferenceGroup) getPreferenceScreen().findPreference(GENERAL_PREF_GROUP)).addPreference(listPreference);
        }

        @NonNull
        private ContextThemeWrapper getContextThemeWrapper() {
            TypedValue themeTypedValue = new TypedValue();
            Context context = getContext();
            context.getTheme().resolveAttribute(R.attr.preferenceTheme, themeTypedValue, true);
            return new ContextThemeWrapper(context, themeTypedValue.resourceId);
        }

        private void handlePreferenceUpdates() {
            HashMap<String, Preference.OnPreferenceChangeListener> onPreferenceChangeHandlersMap = getIndividualPreferenceHandlersMap();
            U.forEach(onPreferenceChangeHandlersMap.keySet(),
                    preferenceKey -> findPreference(preferenceKey)
                            .setOnPreferenceChangeListener(
                                    onPreferenceChangeHandlersMap.get(preferenceKey)
                            ));
        }

        @NonNull
        private HashMap<String, Preference.OnPreferenceChangeListener> getIndividualPreferenceHandlersMap() {
            HashMap<String, Preference.OnPreferenceChangeListener> onPreferenceChangeHandlersMap = new HashMap<>();
            onPreferenceChangeHandlersMap.put(IS_DARK_THEME_ACTIVE_PREFERENCES_KEY, onThemeToggle());
            onPreferenceChangeHandlersMap.put(T9_SEARCH_ENABLED_SHARED_PREF_KEY, onT9SearchToggle());
            onPreferenceChangeHandlersMap.put(WHATSAPP_INTEGRATION_ENABLED_PREFERENCE_KEY, onWhatsappToggle());
            onPreferenceChangeHandlersMap.put(ENABLE_CALL_FILTERING_SHARED_PREF_KEY, onCallFilteringToggle());
            return onPreferenceChangeHandlersMap;
        }

        @NonNull
        private Preference.OnPreferenceChangeListener onCallFilteringToggle() {
            return (preference, newValue) -> {
                if (newValue.equals(false)) return true;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    Toast.makeText(getContext(), R.string.device_not_supported_call_filtering, LENGTH_SHORT).show();
                    return false;
                }
                RoleManager roleManager = (RoleManager) getContext().getSystemService(ROLE_SERVICE);
                if (roleManager.isRoleHeld(ROLE_CALL_SCREENING)) {
                    return true;
                }
                askToBecomeCallScreeningApp(roleManager);
                return false;
            };
        }

        @NonNull
        private Preference.OnPreferenceChangeListener onWhatsappToggle() {
            return (preference, newValue) -> {
                if(newValue.equals(false)) return true;
                if(!isWhatsappInstalled(activity)) {
                    Toast.makeText(activity, R.string.whatsapp_not_installed, Toast.LENGTH_LONG).show();
                    return false;
                }
                showSetDefaultCountryCodeDialog(activity);
                return true;
            };
        }

        @NonNull
        private Preference.OnPreferenceChangeListener onT9SearchToggle() {
            return (preference, newValue) -> {
                activity.recreate();
                return true;
            };
        }

        @NonNull
        private Preference.OnPreferenceChangeListener onThemeToggle() {
            return (preference, newValue) -> {
                TintedDrawablesStore.reset();
                activity.recreate();
                return true;
            };
        }

        @RequiresApi(api = Build.VERSION_CODES.Q)
        private void askToBecomeCallScreeningApp(RoleManager roleManager) {
            Intent intent = roleManager.createRequestRoleIntent(ROLE_CALL_SCREENING);
            startActivityForResult(intent, REQUEST_TO_BECOMING_CALL_SCREENER);
        }

        private void showSetDefaultCountryCodeDialog(Context context) {
            AppCompatEditText countryCodeEditText = new AppCompatEditText(context);
            countryCodeEditText.setText(getDefaultWhatsAppCountryCode(context));
            countryCodeEditText.setInputType(InputType.TYPE_CLASS_PHONE);
            new AlertDialog.Builder(context)
                    .setView(countryCodeEditText)
                    .setTitle(R.string.input_country_calling_code_title)
                    .setMessage(R.string.input_country_calling_code_description)
                    .setPositiveButton(R.string.enable_whatsapp_integration, (dialogInterface, i) -> {
                        if(!isWhatsappInstalled(context)) {
                            showAlert(context, getString(R.string.whatsapp_not_installed), getString(R.string.enable_only_after_installing_whatsapp));
                            return;
                        }
                        enableWhatsappIntegration(countryCodeEditText.getText().toString(), context);
                    })
                    .setNegativeButton(R.string.disable_whatsapp_integration, (ignore_x, ignore_y) -> {
                        disableWhatsappIntegration(context);
                        getActivity().recreate();// recreating coz preference fragment is not able to read the disabled preference and still shows enable.
                    })
                    .show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_TO_BECOMING_CALL_SCREENER) {
            if (resultCode != android.app.Activity.RESULT_OK) return;
            enableCallFiltering(this);
            recreate();
        }
    }
}
