package org.jak_linux.dns66.main;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import org.jak_linux.dns66.Configuration;
import org.jak_linux.dns66.FileHelper;
import org.jak_linux.dns66.MainActivity;
import org.jak_linux.dns66.R;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Allows editing the StevenBlack hosts files with a simple checklist dialog
 */
public class StevenBlackWizard implements DialogInterface.OnMultiChoiceClickListener {

    // base elements
    private Context cntx; // base context
    private final ItemRecyclerViewAdapter listAdapter; // list of items

    /**
     * Constructor
     *
     * @param cntx        base context (for context operations)
     * @param listAdapter where the items are stored and displayed
     */
    public StevenBlackWizard(Context cntx, ItemRecyclerViewAdapter listAdapter) {
        this.cntx = cntx;
        this.listAdapter = listAdapter;
    }

    // ------------------- Public -------------------

    /**
     * Main entry, loads the configuration and shows the dialog
     */
    public void showWizard() {
        // load config
        loadCurrentSettings();

        // show dialog
        new AlertDialog.Builder(cntx)
                .setTitle(R.string.stevenblack_config)
                .setMultiChoiceItems(R.array.stevenblack_items, checked, this) // clicked => #onClick
                .setPositiveButton(R.string.button_apply, (dialog, which) -> saveSettings()) // apply => save
                .setNegativeButton(R.string.button_cancel, null) // cancel => nothing
                .show();
    }

    // ------------------- private -------------------

    // constants
    private static final String BASE_URL_PREFIX = "https://raw.githubusercontent.com/StevenBlack/hosts/master"; // prefix of the base url
    private static final String BASE_URL_SUFFIX = "/hosts"; // suffix of the base url
    private static final String ALT_URL_PREFIX = "/alternates/"; // prefix of the alternate middle part
    private static final String ALT_URL_SEP = "-"; // separator of entries in the alternate part
    private static final String TITLE_ALT_SEP = " + "; // separator of entries for the title part
    private static final Pattern URL_REGEXP = Pattern.compile(BASE_URL_PREFIX + "(" + ALT_URL_PREFIX + "([^/]*))?" + BASE_URL_SUFFIX); // regexp for the url
    private static final String[] ALT_URL_NAMES = {"fakenews", "gambling", "porn", "social"}; // elements in the alternate part (in order)

    // variables
    private boolean[] checked = new boolean[5]; // shown checkboxes in the dialog
    private int hostIndex; // index of host item being edited

    /**
     * When an element in the dialog is clicked
     *
     * @param dialog    the dialog shown
     * @param which     index of the element clicked
     * @param isChecked whether it was checked or unchecked
     */
    @Override
    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
        if (which == 0 && !isChecked) {
            // when 'ads and malware' is disabled, disable the rest
            for (int i = 1; i < 5; ++i) {
                // disable and update all except the first
                checked[i] = false;
                ((AlertDialog) dialog).getListView().setItemChecked(i, false);
            }
        }

        if (which != 0 && isChecked) {
            // enable 'ads and malware' if another one is enabled
            checked[0] = true;
            ((AlertDialog) dialog).getListView().setItemChecked(0, true);
        }
    }

    /**
     * Loads the current settings by finding the entry and extracting the selected elements based on the url and the state
     */
    private void loadCurrentSettings() {
        // find host index

        // start with all disabled (in case nothing is found)
        hostIndex = -1;
        Arrays.fill(checked, false);

        // find the entry, search starting from the bottom one
        for (int i = listAdapter.items.size() - 1; i >= 0; i--) {
            // for each entry check the url
            Configuration.Item item = listAdapter.items.get(i);
            final Matcher matcher = URL_REGEXP.matcher(item.location);
            if (matcher.matches()) {
                // url matches, item found
                hostIndex = i;

                // get elements
                switch (item.state) {
                    case Configuration.Item.STATE_DENY:
                        // item enabled, valid
                        checked[0] = true;

                        // get alternated
                        String group = matcher.group(2);
                        if (group == null) {
                            // the normal file, nothing else checked
                            Arrays.fill(checked, 1, 5, false);
                        } else {
                            // an alternate file, check whether elements are present or not
                            for (int a = 0; a < ALT_URL_NAMES.length; a++) {
                                checked[a + 1] = group.contains(ALT_URL_NAMES[a]);
                            }
                        }
                        // stop search
                        return;
                    case Configuration.Item.STATE_ALLOW:
                        // item allowed? treat as ignored
                    case Configuration.Item.STATE_IGNORE:
                        // item disabled, set all as unchecked
                        Arrays.fill(checked, false);
                        // but continue searching in case there is another enabled file
                        break;
                }
            }
        }
    }

    /**
     * Apply the selected settings
     */
    private void saveSettings() {
        // get item to edit
        Configuration.Item item;
        if (hostIndex == -1) {
            // no old entry, create new at the beginning
            item = new Configuration.Item();
            listAdapter.items.add(0, item);
        } else {
            // get found entry
            item = listAdapter.items.get(hostIndex);
        }

        // create item properties
        StringBuilder middle_url = new StringBuilder(); // the url middle part
        StringBuilder title = new StringBuilder(FileHelper.loadDefaultSettings(cntx).hosts.items.get(0).title); // the title of the entry
        int state;

        if (!checked[0]) {
            // disabled
            state = Configuration.Item.STATE_IGNORE;
        } else {
            // enabled
            state = Configuration.Item.STATE_DENY;

            // update title and middle_url
            for (int i = 0; i < ALT_URL_NAMES.length; i++) {
                if (checked[i + 1]) {
                    middle_url.append(middle_url.length() == 0 ? ALT_URL_PREFIX : ALT_URL_SEP) // if first, add the prefix, otherwise add the separator
                            .append(ALT_URL_NAMES[i]);
                    title.append(TITLE_ALT_SEP)
                            .append(cntx.getResources().getStringArray(R.array.stevenblack_items)[i + 1]);
                }
            }
        }

        // set item settings
        item.state = state;
        item.location = BASE_URL_PREFIX + middle_url.toString() + BASE_URL_SUFFIX;
        item.title = title.toString();

        // update
        listAdapter.notifyDataSetChanged();
        FileHelper.writeSettings(cntx, MainActivity.config);
    }

}
