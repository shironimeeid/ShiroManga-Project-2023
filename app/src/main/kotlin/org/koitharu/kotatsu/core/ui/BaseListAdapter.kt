package org.koitharu.kotatsu.core.ui

import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer.ListListener
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import com.hannesdorfmann.adapterdelegates4.AsyncListDifferDelegationAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.FlowCollector
import org.koitharu.kotatsu.core.util.ContinuationResumeRunnable
import org.koitharu.kotatsu.list.ui.ListModelDiffCallback
import org.koitharu.kotatsu.list.ui.adapter.ListItemType
import org.koitharu.kotatsu.list.ui.model.ListModel
import kotlin.coroutines.suspendCoroutine

open class BaseListAdapter<T : ListModel> : AsyncListDifferDelegationAdapter<T>(
	AsyncDifferConfig.Builder(ListModelDiffCallback<T>())
		.setBackgroundThreadExecutor(Dispatchers.Default.limitedParallelism(2).asExecutor())
		.build(),
), FlowCollector<List<T>?> {

	override suspend fun emit(value: List<T>?) = suspendCoroutine { cont ->
		setItems(value.orEmpty(), ContinuationResumeRunnable(cont))
	}

	fun addDelegate(type: ListItemType, delegate: AdapterDelegate<List<T>>): BaseListAdapter<T> {
		delegatesManager.addDelegate(type.ordinal, delegate)
		return this
	}

	fun addListListener(listListener: ListListener<T>) {
		differ.addListListener(listListener)
	}

	fun removeListListener(listListener: ListListener<T>) {
		differ.removeListListener(listListener)
	}
}
