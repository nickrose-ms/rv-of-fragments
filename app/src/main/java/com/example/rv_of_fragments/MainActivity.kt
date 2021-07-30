package com.example.rv_of_fragments

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.doOnAttach
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.*
import androidx.recyclerview.widget.RecyclerView
import com.example.rv_of_fragments.databinding.ActivityMainBinding
import com.example.rv_of_fragments.databinding.ColorFragmentBinding
import com.example.rv_of_fragments.databinding.ItemBinding
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = Adapter(items, supportFragmentManager)
        binding.recyclerView.adapter = adapter
    }

    companion object {
        private val items = List(50) {
            Item(
                tag = it.toString(),
                color = randomColor(),
                number = it + 1
            )
        }
    }
}

data class Item(
    val tag: String,
    val color: Int,
    val number: Int
)

class Adapter(
    private val items: List<Item>,
    private val fragmentManager: FragmentManager
) : RecyclerView.Adapter<FragmentViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FragmentViewHolder {
        return LayoutInflater.from(parent.context)
            .let { layoutInflater -> ItemBinding.inflate(layoutInflater, parent, false) }
            .let { binding -> FragmentViewHolder(binding, fragmentManager) }
    }

    override fun onBindViewHolder(holder: FragmentViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onViewDetachedFromWindow(holder: FragmentViewHolder) {
        holder.onDetachedFromWindow()
    }
}

// todo: need to GC Fragments at appropriate times
// todo: need to GC Fragments that are offscreen after process recreation
// todo: how does FragmentStateAdapter.mSavedStates work?
class FragmentViewHolder(
    private val binding: ItemBinding,
    private val fragmentManager: FragmentManager
) : RecyclerView.ViewHolder(binding.root) {
    private var item: Item? = null

    init {
        binding.root.apply {
            id = View.generateViewId()
            isSaveEnabled = false
        }
    }

    // Always allow state loss commits in case user is scrolling quickly, then backgrounds the app
    // and then a Fragment transaction is committed after state is saved.
    fun bind(item: Item) {
        Log.d("qwer", "Fragment count: ${fragmentManager.fragments.size}")
        this.item = item
        val fragment = fragmentManager.findFragmentByTag(item.tag)
        if (fragment != null) {
            // Reparent the Fragment View to this ViewHolder
            binding.root.removeAllViews()
            val view = fragment.view
            if (view != null) {
                (view.parent as? ViewGroup)?.removeView(view)
                binding.root.addView(view)
            }
            fragmentManager.beginTransaction()
                .setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
                .commitNowAllowingStateLoss()
        } else {
            // Need to wait for View to be attached before its ID can be used in a Fragment transaction.
            binding.root.doOnAttach {
                fragmentManager.beginTransaction()
                    .add(binding.root.id, ColorFragment.create(item.color, item.number), item.tag)
                    .commitAllowingStateLoss()
            }
        }
    }

    fun onDetachedFromWindow() {
        val item = item ?: return
        val fragment = fragmentManager.findFragmentByTag(item.tag) ?: return
        fragmentManager.beginTransaction()
            .setMaxLifecycle(fragment, Lifecycle.State.STARTED)
            .commitNowAllowingStateLoss()
    }
}

fun randomColor() = Color.argb(255, Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))

class ColorFragment : Fragment(R.layout.color_fragment) {
    private val viewModel by lazy {
        val factory = ColorViewModel.Factory(requireArguments().getInt("number"))
        ViewModelProvider(this, factory).get(ColorViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = ColorFragmentBinding.bind(view)
        binding.root.background = ColorDrawable(requireArguments().getInt("color"))

        viewModel.counter
            .onEach { binding.number.text = it.toString() }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        fun create(color: Int, number: Int): Fragment {
            return ColorFragment().apply {
                arguments = bundleOf(
                    "color" to color,
                    "number" to number
                )
            }
        }
    }

    class ColorViewModel(private val initial: Int) : ViewModel() {
        val counter = flow {
            var counter = initial
            while (currentCoroutineContext().isActive) {
                emit(counter++)
                delay(1000L)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = initial
        )

        class Factory(private val initial: Int) : ViewModelProvider.Factory {
            override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                return ColorViewModel(initial) as T
            }
        }
    }
}