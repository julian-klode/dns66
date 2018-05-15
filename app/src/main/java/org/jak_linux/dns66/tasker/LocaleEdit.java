package org.jak_linux.dns66.tasker;

import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import org.jak_linux.dns66.R;
import org.slf4j.helpers.Util;


public class LocaleEdit extends AppCompatActivity {
    private boolean mIsCancelled = false;

    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        BundleScrubber.scrub(getIntent());
        BundleScrubber.scrub(getIntent().getBundleExtra(
                com.twofortyfouram.locale.Intent.EXTRA_BUNDLE));

        setContentView(R.layout.activity_tasker);

        setupTitleApi11();

        if (null == paramBundle) {
            final Bundle forwardedBundle = getIntent().getBundleExtra(
                    com.twofortyfouram.locale.Intent.EXTRA_BUNDLE);
            if (PluginBundleManager.isBundleValid(forwardedBundle)) {
                String status = forwardedBundle.getString(PluginBundleManager.BUNDLE_EXTRA_STRING_MESSAGE);
                RadioButton radioEnable = (RadioButton) findViewById(R.id.tasker_enable_dns);
                RadioButton radioDisable = (RadioButton) findViewById(R.id.tasker_disable_dns);

                if(status.equals("enable")){
                    radioEnable.setChecked(true);
                }
                else if(status.equals("disable")){
                    radioDisable.setChecked(true);
                }
            }
        }
    }

    private void setupTitleApi11() {
        CharSequence callingApplicationLabel = null;
        try {
            callingApplicationLabel = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(getCallingPackage(),
                            0));
        } catch (final NameNotFoundException e) {
        }
        if (null != callingApplicationLabel) {
            setTitle(callingApplicationLabel);
        }
    }

    protected void onPause() {
        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.twofortyfouram_locale_menu_dontsave:
                mIsCancelled = true;
                finish();
                return true;
            case R.id.twofortyfouram_locale_menu_save:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.tasker_menu, menu);
        return true;
    }

    @Override
    public void finish() {
        if (mIsCancelled) {
            setResult(RESULT_CANCELED);
        } else {
            RadioButton radioEnable = (RadioButton) findViewById(R.id.tasker_enable_dns);
            RadioButton radioDisable = (RadioButton) findViewById(R.id.tasker_disable_dns);

            String action = "na";

            if (radioEnable.isChecked()) {
                action = "enable";
            } else if (radioDisable.isChecked()) {
                action = "disable";
            }
            final Intent resultIntent = new Intent();
            resultIntent.putExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE, PluginBundleManager.generateBundle(getApplicationContext(), action));
            resultIntent.putExtra(com.twofortyfouram.locale.Intent.EXTRA_STRING_BLURB, action);
            setResult(RESULT_OK, resultIntent);
        }
        super.finish();
    }
}
