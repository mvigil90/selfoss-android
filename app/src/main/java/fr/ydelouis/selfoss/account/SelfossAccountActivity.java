package fr.ydelouis.selfoss.account;

import android.accounts.AccountAuthenticatorActivity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.CheckedChange;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.EditorAction;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.SystemService;
import org.androidannotations.annotations.TextChange;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.rest.RestService;

import java.io.IOException;

import fr.ydelouis.selfoss.R;
import fr.ydelouis.selfoss.adapter.SyncPeriodAdapter;
import fr.ydelouis.selfoss.entity.Success;
import fr.ydelouis.selfoss.rest.SelfossRest;
import fr.ydelouis.selfoss.sync.SyncManager;
import fr.ydelouis.selfoss.sync.SyncPeriod;

@EActivity(R.layout.activity_selfossaccount)
public class SelfossAccountActivity extends AccountAuthenticatorActivity {

	private static final long TIME_TO_CLOSE = 1500;

	@Bean protected SelfossAccount account;
	@Bean protected SyncManager syncManager;
	@RestService protected SelfossRest selfossRest;
	@SystemService protected InputMethodManager inputMethodManager;

	@ViewById protected EditText url;
	@ViewById protected CheckBox requireAuth;
	@ViewById protected View usernamePasswordContainer;
	@ViewById protected EditText username;
	@ViewById protected EditText password;
	@ViewById protected Spinner period;
	@ViewById protected View validate;
	@ViewById protected View progress;
	@ViewById protected TextView validateText;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getActionBar().setDisplayHomeAsUpEnabled(true);
	}

	@AfterViews
	protected void updateUi() {
		url.setText(getDisplayedUrl());
		requireAuth.setChecked(account.requireAuth());
		username.setText(getDisplayedUsername());
		password.setText(account.getPassword());
		period.setAdapter(new SyncPeriodAdapter(this));
		period.setSelection(SyncPeriod.indexOf(account.getSyncPeriod()));
	}

	private String getDisplayedUrl() {
		String url = account.getUrl();
		if (account.requireAuth() != account.useHttps()) {
			if (account.useHttps()) {
				url = "https://" + url;
			} else {
				url = "http://" + url;
			}
		}
		return url;
	}

	private String getDisplayedUsername() {
		String url = account.getUrl();
		String username = account.getUsername();
		return url.equals(username) ? "" : username;
	}

	@CheckedChange(R.id.requireAuth)
	protected void onProtectedStateChange(boolean isChecked) {
		usernamePasswordContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
	}

	@Click(R.id.validate)
	@EditorAction(R.id.password)
	protected void onValidate() {
		hydrate();
		showProgress();
		tryLogin();
	}

	private void hydrate() {
		String url = this.url.getText().toString();
		boolean useHttps = useHttps(url);
		url = removeScheme(url);
		long syncPeriod = SyncPeriod.values()[period.getSelectedItemPosition()].getTime();
		if (requireAuth.isChecked()) {
			String username = this.username.getText().toString();
			String password = this.password.getText().toString();
			account.create(url, username, password, useHttps, syncPeriod);
		} else {
			account.create(url, useHttps, syncPeriod);
		}
		account.setTrustAllCertificates(false);
	}

	private boolean useHttps(String url) {
		if (url.startsWith("https://")) {
			return true;
		} else if (url.startsWith("http://")) {
			return false;
		} else {
			return requireAuth.isChecked();
		}
	}

	private String removeScheme(String url) {
		String schemeMark = "://";
		int start = url.indexOf(schemeMark);
		if (start != -1) {
			return url.substring(start+schemeMark.length());
		}
		return url;
	}

	protected void showProgress() {
		progress.setVisibility(View.VISIBLE);
		validateText.setText(R.string.checking);
		validate.setEnabled(false);
		inputMethodManager.hideSoftInputFromWindow(url.getWindowToken(), 0);
	}

	@Background
	protected void tryLogin() {
		try {
			Success success = selfossRest.login();
			handleSuccess(success);
		} catch (Exception e) {
			handleException(e);
		}
	}

	private void handleSuccess(Success success) {
		if (success.isSuccess()) {
			showSuccess();
			quitDelayed();
		} else {
			showUsernamePasswordError();
		}
	}

	private void handleException(Exception e) {
		if (isCertificateException(e)) {
			showCertificateError();
		} else {
			showUrlError();
			e.printStackTrace();
		}
	}

	private boolean isCertificateException(Exception e) {
		return e.getCause() instanceof IOException
			&& e.getMessage().contains("Hostname")
			&& e.getMessage().contains("was not verified");
	}

	private void hideProgress() {
		progress.setVisibility(View.GONE);
		validate.setEnabled(true);
		validateText.setText(R.string.validate);
	}

	@UiThread
	protected void showSuccess() {
		progress.setVisibility(View.GONE);
		validate.setBackgroundResource(R.drawable.bg_button_success);
		validateText.setText(R.string.success);
	}

	@UiThread(delay = TIME_TO_CLOSE)
	public void quitDelayed() {
		syncManager.requestSync();
		finish();
	}

	@UiThread
	protected void showUrlError() {
		showError();
		url.setError(getString(R.string.error_url));
	}

	@UiThread
	protected void showUsernamePasswordError() {
		showError();
		requireAuth.setChecked(true);
		password.setError(getString(R.string.error_usernamePassword));
	}

	@UiThread
	protected void showCertificateError() {
		showError();
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.certificate_error);
		builder.setMessage(getString(R.string.certificate_error_message, account.getUrl()));
		builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				trustAllCertificates();
			}
		});
		builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				hideError();
			}
		});
		builder.show();
	}

	private void showError() {
		hideProgress();
		validate.setBackgroundResource(R.drawable.bg_button_error);
		validateText.setText(R.string.error);
	}


	@TextChange({ R.id.url, R.id.username, R.id.password })
	protected void hideError() {
		validate.setBackgroundResource(R.drawable.bg_button_default);
		validateText.setText(R.string.validate);
	}

	private void trustAllCertificates() {
		account.setTrustAllCertificates(true);
		validate.setBackgroundResource(R.drawable.bg_button_default);
		showProgress();
		tryLogin();
	}

	@OptionsItem(android.R.id.home)
	protected void quit() {
		finish();
	}
}
