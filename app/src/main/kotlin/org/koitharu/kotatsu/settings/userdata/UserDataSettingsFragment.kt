package org.koitharu.kotatsu.settings.userdata

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.postDelayed
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import androidx.preference.forEach
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.StateFlow
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.exceptions.resolve.SnackbarErrorObserver
import org.koitharu.kotatsu.core.os.AppShortcutManager
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.BasePreferenceFragment
import org.koitharu.kotatsu.core.ui.util.ActivityRecreationHandle
import org.koitharu.kotatsu.core.ui.util.ReversibleActionObserver
import org.koitharu.kotatsu.core.util.FileSize
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.tryLaunch
import org.koitharu.kotatsu.local.data.CacheDir
import org.koitharu.kotatsu.settings.backup.BackupDialogFragment
import org.koitharu.kotatsu.settings.backup.RestoreDialogFragment
import org.koitharu.kotatsu.settings.protect.ProtectSetupActivity
import javax.inject.Inject

@AndroidEntryPoint
class UserDataSettingsFragment : BasePreferenceFragment(R.string.data_and_privacy),
	SharedPreferences.OnSharedPreferenceChangeListener,
	ActivityResultCallback<Uri?> {

	@Inject
	lateinit var appShortcutManager: AppShortcutManager

	@Inject
	lateinit var activityRecreationHandle: ActivityRecreationHandle

	private val viewModel: UserDataSettingsViewModel by viewModels()

	private val backupSelectCall = registerForActivityResult(
		ActivityResultContracts.OpenDocument(),
		this,
	)

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_user_data)
		findPreference<Preference>(AppSettings.KEY_SHORTCUTS)?.isVisible =
			appShortcutManager.isDynamicShortcutsAvailable()
		findPreference<TwoStatePreference>(AppSettings.KEY_PROTECT_APP)
			?.isChecked = !settings.appPassword.isNullOrEmpty()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		findPreference<Preference>(AppSettings.KEY_PAGES_CACHE_CLEAR)?.bindBytesSizeSummary(checkNotNull(viewModel.cacheSizes[CacheDir.PAGES]))
		findPreference<Preference>(AppSettings.KEY_THUMBS_CACHE_CLEAR)?.bindBytesSizeSummary(checkNotNull(viewModel.cacheSizes[CacheDir.THUMBS]))
		findPreference<Preference>(AppSettings.KEY_HTTP_CACHE_CLEAR)?.bindBytesSizeSummary(viewModel.httpCacheSize)
		findPreference<Preference>(AppSettings.KEY_SEARCH_HISTORY_CLEAR)?.let { pref ->
			viewModel.searchHistoryCount.observe(viewLifecycleOwner) {
				pref.summary = if (it < 0) {
					view.context.getString(R.string.loading_)
				} else {
					pref.context.resources.getQuantityString(R.plurals.items, it, it)
				}
			}
		}
		findPreference<Preference>(AppSettings.KEY_UPDATES_FEED_CLEAR)?.let { pref ->
			viewModel.feedItemsCount.observe(viewLifecycleOwner) {
				pref.summary = if (it < 0) {
					view.context.getString(R.string.loading_)
				} else {
					pref.context.resources.getQuantityString(R.plurals.items, it, it)
				}
			}
		}
		findPreference<StorageUsagePreference>("storage_usage")?.let { pref ->
			viewModel.storageUsage.observe(viewLifecycleOwner, pref)
		}
		viewModel.loadingKeys.observe(viewLifecycleOwner) { keys ->
			preferenceScreen.forEach { pref ->
				pref.isEnabled = pref.key !in keys
			}
		}
		viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(listView, this))
		viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(listView))
		settings.subscribe(this)
	}

	override fun onDestroyView() {
		settings.unsubscribe(this)
		super.onDestroyView()
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			AppSettings.KEY_PAGES_CACHE_CLEAR -> {
				viewModel.clearCache(preference.key, CacheDir.PAGES)
				true
			}

			AppSettings.KEY_THUMBS_CACHE_CLEAR -> {
				viewModel.clearCache(preference.key, CacheDir.THUMBS)
				true
			}

			AppSettings.KEY_COOKIES_CLEAR -> {
				clearCookies()
				true
			}

			AppSettings.KEY_SEARCH_HISTORY_CLEAR -> {
				clearSearchHistory()
				true
			}

			AppSettings.KEY_HTTP_CACHE_CLEAR -> {
				viewModel.clearHttpCache()
				true
			}

			AppSettings.KEY_UPDATES_FEED_CLEAR -> {
				viewModel.clearUpdatesFeed()
				true
			}

			AppSettings.KEY_BACKUP -> {
				BackupDialogFragment.show(childFragmentManager)
				true
			}

			AppSettings.KEY_RESTORE -> {
				if (!backupSelectCall.tryLaunch(arrayOf("*/*"))) {
					Snackbar.make(
						listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT,
					).show()
				}
				true
			}

			AppSettings.KEY_PROTECT_APP -> {
				val pref = (preference as? TwoStatePreference ?: return false)
				if (pref.isChecked) {
					pref.isChecked = false
					startActivity(Intent(preference.context, ProtectSetupActivity::class.java))
				} else {
					settings.appPassword = null
				}
				true
			}

			else -> super.onPreferenceTreeClick(preference)
		}
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		when (key) {
			AppSettings.KEY_APP_PASSWORD -> {
				findPreference<TwoStatePreference>(AppSettings.KEY_PROTECT_APP)
					?.isChecked = !settings.appPassword.isNullOrEmpty()
			}

			AppSettings.KEY_THEME -> {
				AppCompatDelegate.setDefaultNightMode(settings.theme)
			}

			AppSettings.KEY_COLOR_THEME,
			AppSettings.KEY_THEME_AMOLED -> {
				postRestart()
			}

			AppSettings.KEY_APP_LOCALE -> {
				AppCompatDelegate.setApplicationLocales(settings.appLocales)
			}
		}
	}

	override fun onActivityResult(result: Uri?) {
		if (result != null) {
			RestoreDialogFragment.show(childFragmentManager, result)
		}
	}

	private fun Preference.bindBytesSizeSummary(stateFlow: StateFlow<Long>) {
		stateFlow.observe(viewLifecycleOwner) { size ->
			summary = if (size < 0) {
				context.getString(R.string.computing_)
			} else {
				FileSize.BYTES.format(context, size)
			}
		}
	}

	private fun clearSearchHistory() {
		MaterialAlertDialogBuilder(context ?: return)
			.setTitle(R.string.clear_search_history)
			.setMessage(R.string.text_clear_search_history_prompt)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.clear) { _, _ ->
				viewModel.clearSearchHistory()
			}.show()
	}

	private fun clearCookies() {
		MaterialAlertDialogBuilder(context ?: return)
			.setTitle(R.string.clear_cookies)
			.setMessage(R.string.text_clear_cookies_prompt)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.clear) { _, _ ->
				viewModel.clearCookies()
			}.show()
	}

	private fun postRestart() {
		view?.postDelayed(400) {
			activityRecreationHandle.recreateAll()
		}
	}

}
