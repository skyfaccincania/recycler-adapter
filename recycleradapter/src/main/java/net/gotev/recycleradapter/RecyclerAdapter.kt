package net.gotev.recycleradapter

import android.view.ViewGroup
import androidx.core.util.Pair
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.UP
import androidx.recyclerview.widget.RecyclerView
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator

/**
 * Makes the use of RecyclerView easier, modular and less error-prone
 *
 * @author Aleksandar Gotev
 */
class RecyclerAdapter : RecyclerView.Adapter<RecyclerAdapterViewHolder>(), RecyclerAdapterNotifier {

    private val itemsList = ArrayList<AdapterItem<in RecyclerAdapterViewHolder>>()
    private val types = LinkedHashMap<Int, AdapterItem<*>>()
    private var emptyItem: AdapterItem<in RecyclerAdapterViewHolder>? = null

    private var filtered = ArrayList<AdapterItem<in RecyclerAdapterViewHolder>>()
    private var showFiltered = false

    private val items
        get() = if (showFiltered) filtered else itemsList

    init {
        setHasStableIds(true)
    }

    private fun notifyChangedPosition(position: Int) {
        notifyItemChanged(position, true)
    }

    private fun Int.isOutOfItemsRange() = this < 0 || this >= items.size

    private fun adapterIsEmptyAndEmptyItemIsDefined() = items.isEmpty() && emptyItem != null

    private fun updateItemAtPosition(
        item: AdapterItem<in RecyclerAdapterViewHolder>,
        position: Int
    ) {
        items[position] = item
        notifyChangedPosition(position)
    }

    private fun registerItemType(item: AdapterItem<*>) {
        val classType = item.viewType()

        if (!types.containsKey(classType)) {
            types[classType] = item
        }
    }

    private fun removeEmptyItemIfItHasBeenConfigured(insertPosition: Int) {
        // this is necessary to prevent IndexOutOfBoundsException on RecyclerView when the
        // first item gets added and an empty item has been configured
        if (insertPosition == 0 && items.size >= 1 && emptyItem != null) {
            notifyItemChanged(0)
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (adapterIsEmptyAndEmptyItemIsDefined()) {
            return emptyItem.viewType()
        }

        return items[position].viewType()
    }

    override fun getItemId(position: Int) = if(adapterIsEmptyAndEmptyItemIsDefined()) {
        emptyItem.hashCode().toLong()
    } else {
        items[position].diffingId().hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerAdapterViewHolder {
        val item = if (adapterIsEmptyAndEmptyItemIsDefined() && viewType == emptyItem.viewType()) {
            emptyItem!!
        } else {
            types.getValue(viewType)
        }

        return item.createItemViewHolder(parent)
    }

    private fun bindItem(holder: RecyclerAdapterViewHolder, position: Int, firstTime: Boolean) {
        val item = if (adapterIsEmptyAndEmptyItemIsDefined()) {
            emptyItem!!
        } else {
            items[position]
        }

        holder.setAdapter(this)
        item.bind(firstTime, holder)
    }

    override fun onBindViewHolder(holder: RecyclerAdapterViewHolder, position: Int, payloads: MutableList<Any>) {
        bindItem(holder, position, payloads.isEmpty())
    }

    override fun onBindViewHolder(holder: RecyclerAdapterViewHolder, position: Int) {
        bindItem(holder, position, true)
    }

    override fun onViewRecycled(holder: RecyclerAdapterViewHolder) {
        super.onViewRecycled(holder)
        holder.prepareForReuse()
    }

    override fun getItemCount() = if (adapterIsEmptyAndEmptyItemIsDefined()) 1 else items.size

    /**
     * Gets the index of the last item in the list.
     *
     * @return index
     */
    val lastItemIndex
        get() = items.lastIndex

    override fun getAdapterItem(holder: RecyclerAdapterViewHolder): AdapterItem<*>? {
        val position = holder.adapterPosition.takeIf { !it.isOutOfItemsRange() } ?: return null

        return items[position]
    }

    override fun notifyItemChanged(holder: RecyclerAdapterViewHolder) {
        val position = holder.adapterPosition.takeIf { !it.isOutOfItemsRange() } ?: return

        notifyChangedPosition(position)
    }

    /**
     * Sets the item to show when the recycler adapter is empty.
     *
     * @param item item to show when the recycler adapter is empty
     */
    fun setEmptyItem(item: AdapterItem<*>?) {
        val previouslyEmpty = emptyItem == null
        val afterEmpty = item == null

        emptyItem = item?.castAsIn()

        if (items.isEmpty()) {
            if (previouslyEmpty && !afterEmpty) {
                notifyItemInserted(0)
            } else if (!previouslyEmpty && afterEmpty) {
                notifyItemRemoved(0)
            } else if (!previouslyEmpty && !afterEmpty) {
                notifyItemChanged(0)
            }
        }
    }

    /**
     * Adds a new item to this adapter
     *
     * @param item item to add
     * @param position position at which to add the element. If null, the element will be added
     * at the end of the list, otherwise the item will be inserted at (position) and all the
     * existing items starting from (position) will be shifted forward.
     * @return [RecyclerAdapter]
     */
    fun add(item: AdapterItem<*>, position: Int? = null): RecyclerAdapter {
        val insertPosition = if (position != null) {
            when {
                position >= items.size -> {
                    items.add(item.castAsIn())
                    items.lastIndex
                }

                position < 0 -> {
                    items.add(0, item.castAsIn())
                    0
                }

                else -> {
                    items.add(position, item.castAsIn())
                    position
                }
            }
        } else {
            items.add(item.castAsIn())
            items.lastIndex
        }

        registerItemType(item)
        removeEmptyItemIfItHasBeenConfigured(insertPosition)
        notifyItemInserted(insertPosition)
        return this
    }

    /**
     * Adds many items to this adapter.
     *
     * This method has better performance when inserting many items if compared to multiple calls
     * of the single [add] method.
     *
     * @param newItems items to add
     * @param startingPosition position at which to start adding new elements. If null, the elements
     * will be added at the end of the list, otherwise the items will be inserted starting from
     * (startingPosition) and all the existing items starting from (startingPosition) will be
     * shifted forward.
     * @return [RecyclerAdapter]
     */
    fun add(newItems: List<AdapterItem<*>>, startingPosition: Int? = null): RecyclerAdapter {
        if (newItems.isEmpty()) return this

        val firstIndex = items.size

        if (startingPosition == null) {
            /*
             This does not work in Kotlin 1.4.x
             items.addAll(newItems.map {
                 registerItemType(it)
                 it.castAsIn()
             })
             */
            items.ensureCapacity(items.size + newItems.size)
            newItems.forEach {
                registerItemType(it)
                items.add(it.castAsIn())
            }
        } else {
            newItems.reversed().forEach {
                registerItemType(it)
                items.add(startingPosition, it.castAsIn())
            }
        }

        removeEmptyItemIfItHasBeenConfigured(firstIndex)
        notifyItemRangeInserted(firstIndex, newItems.size)
        return this
    }

    /**
     * Adds an item into the adapter or updates it if already existing.
     *
     * For the update to work properly, all the items has to override the [AdapterItem.equals]
     * and [AdapterItem.hashCode] methods and implement the required business logic code to detect
     * if two instances are referring to the same item.
     *
     * If the item already exists in the list, by implementing [AdapterItem.hasToBeReplacedBy]
     * in your AdapterItem, you can decide when the new item should replace the existing one in
     * the list, reducing the workload of the recycler view.
     *
     * Check [AdapterItem.hasToBeReplacedBy] method JavaDoc for more information.
     *
     * @param item item to add or update
     * @return [RecyclerAdapter]
     */
    fun addOrUpdate(item: AdapterItem<*>): RecyclerAdapter {
        val itemIndex = items.indexOf(item).takeIf { it >= 0 } ?: return add(item)

        if (items[itemIndex].hasToBeReplacedBy(item)) {
            updateItemAtPosition(item.castAsIn(), itemIndex)
        }

        return this
    }

    /**
     * Adds or updates many items to this adapter.
     * Check [addOrUpdate] for more detailed information
     *
     * @param items items to add
     * @return [RecyclerAdapter]
     */
    fun addOrUpdate(items: List<AdapterItem<*>>): RecyclerAdapter {
        //TODO: this can be improved for performance by getting all the new added positions
        //and all the updated positions
        items.forEach { addOrUpdate(it) }
        return this
    }

    /**
     * Gets the position of an item in an adapter.
     *
     * For this method to work properly, all the items has to override the [AdapterItem.equals]
     * and [AdapterItem.hashCode] methods and implement the required business logic code to detect
     * if two instances are referring to the same item.
     *
     * @param item item object
     * @return the item's position or -1 if the item does not exist
     */
    fun getItemPosition(item: AdapterItem<*>) = items.indexOf(item)

    /**
     * Syncs the internal list of items with a list passed as parameter.
     * Adds, updates or deletes internal items, with RecyclerView animations.
     *
     *
     * For the sync to work properly, all the items has to override the
     * [AdapterItem.equals] and [AdapterItem.hashCode] methods and
     * implement the required business logic code to detect if two instances are referring to the
     * same item. Check the example in [RecyclerAdapter.add].
     * If two instances are referring to the same item, you can decide if the item should be
     * replaced by the new one, by implementing [AdapterItem.hasToBeReplacedBy].
     * Check hasToBeReplacedBy method JavaDoc for more information.
     *
     * @param newItems list of new items. Passing a null or empty list will result in
     * [RecyclerAdapter.clear] method call.
     * @return [RecyclerAdapter]
     */
    fun syncWithItems(newItems: ArrayList<out AdapterItem<*>>): RecyclerAdapter {
        if (newItems.isEmpty()) {
            clear()
            return this
        }

        items.ensureCapacity(newItems.size)

        newItems.forEachIndexed { newItemsIndex, newItem ->
            val internalItemIndex = items.indexOf(newItem)

            if (internalItemIndex < 0) { // new item does not exist
                add(newItem, newItemsIndex)
            } else {
                val internalItem = items[internalItemIndex]

                if (internalItem.hasToBeReplacedBy(newItem)) {
                    if (internalItemIndex != newItemsIndex) {
                        removeItemAtPosition(internalItemIndex)
                        add(newItem, newItemsIndex)
                    } else {
                        items[internalItemIndex] = newItem.castAsIn()
                        registerItemType(newItem)
                        removeEmptyItemIfItHasBeenConfigured(internalItemIndex)
                        notifyChangedPosition(internalItemIndex)
                    }
                } else {
                    if (internalItemIndex != newItemsIndex) {
                        removeItemAtPosition(internalItemIndex)
                        add(internalItem, newItemsIndex)
                    }
                }
            }
        }

        items.filter { newItems.indexOf(it) < 0 }.forEach { removeItem(it) }

        return this
    }

    /**
     * Removes an item from the adapter.
     *
     *
     * For the remove to work properly, all the items has to override the
     * [AdapterItem.equals] and [AdapterItem.hashCode] methods.
     * Check the example in [RecyclerAdapter.addOrUpdate]
     *
     * @param item item to remove
     * @return true if the item has been correctly removed or false if the item does not exist
     */
    fun removeItem(item: AdapterItem<*>): Boolean {
        val itemIndex = items.indexOf(item).takeIf { it >= 0 } ?: return false
        return removeItemAtPosition(itemIndex)
    }

    /**
     * Removes all the items with a certain class from this adapter and automatically notifies changes.
     *
     * @param clazz    class of the items to be removed
     * @param listener listener invoked for every item that is found. If the callback returns true,
     * the item will be removed. If it returns false, the item will not be removed
     */
    @JvmOverloads
    fun removeAllItemsWithClass(clazz: Class<out AdapterItem<*>>,
                                listener: RemoveListener = object : RemoveListener {
                                    override fun hasToBeRemoved(item: AdapterItem<*>) = true
                                }) {
        if (items.isEmpty())
            return

        val iterator = items.listIterator()
        var index: Int
        while (iterator.hasNext()) {
            index = iterator.nextIndex()
            val item = iterator.next()
            if (item.javaClass.name == clazz.name && listener.hasToBeRemoved(item)) {
                iterator.remove()
                notifyItemRemoved(index)
            }
        }

        //TODO: check for type removal in all the other remove methods if the last of a kind has been removed
        if (types.containsKey(clazz.viewType())) {
            types.remove(clazz.viewType())
        }
    }

    /**
     * Gets the last item with a given class, together with its position.
     *
     * @param clazz class of the item to search
     * @return Pair with position and AdapterItem or null if the adapter is empty or no items
     * exists with the given class
     */
    fun getLastItemWithClass(clazz: Class<out AdapterItem<*>>): Pair<Int, AdapterItem<*>>? {
        if (items.isEmpty())
            return null

        for (i in items.lastIndex downTo 0) {
            if (items[i].javaClass.name == clazz.name) {
                return Pair(i, items[i])
            }
        }

        return null
    }

    /**
     * Removes only the last item with a certain class from the adapter.
     *
     * @param clazz class of the item to remove
     */
    fun removeLastItemWithClass(clazz: Class<out AdapterItem<*>>) {
        items.takeIf { !it.isEmpty() }?.let { items ->
            for (i in items.lastIndex downTo 0) {
                if (items[i].javaClass.name == clazz.name) {
                    items.removeAt(i)
                    notifyItemRemoved(i)
                    break
                }
            }
        }
    }

    /**
     * Removes an item in a certain position. Does nothing if the adapter is empty or if the
     * position specified is out of adapter bounds.
     *
     * @param position position to be removed
     * @return true if the item has been removed, false if it doesn't exist or the position
     * is out of bounds
     */
    fun removeItemAtPosition(position: Int) =
            items.takeIf { !it.isEmpty() && !position.isOutOfItemsRange() }?.let { items ->
                items.removeAt(position)
                notifyItemRemoved(position)
                true
            } ?: false

    /**
     * Gets an item at a given position.
     *
     * @param position item position
     * @return [AdapterItem] or null if the adapter is empty or the position is out of bounds
     */
    fun getItemAtPosition(position: Int): AdapterItem<*>? =
            items.takeIf { !it.isEmpty() && !position.isOutOfItemsRange() }
                    ?.let { items -> items[position] }

    /**
     * Clears all the elements in the adapter.
     */
    fun clear() {
        items.let {
            val itemsSize = it.size
            it.clear()
            if (itemsSize > 0) {
                notifyItemRangeRemoved(0, itemsSize)
            }
        }
    }

    /**
     * Enables reordering of the list through drag and drop, which is activated when the user
     * long presses on an item.
     *
     * @param recyclerView recycler view on which to apply the drag and drop
     * @param directions directions on which to enable drag and drop gestures. By default it's
     *                   DOWN or UP but you can set it to DOWN or UP or START or END in case you
     *                   have a grid layout and you want also to drag and drop in all directions
     */
    fun enableDragDrop(recyclerView: RecyclerView, directions: Int = DOWN or UP) {
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                return ItemTouchHelper.Callback.makeFlag(ItemTouchHelper.ACTION_STATE_DRAG, directions)
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val sourcePosition = viewHolder.adapterPosition
                val targetPosition = target.adapterPosition

                Collections.swap(items, sourcePosition, targetPosition)
                notifyItemMoved(sourcePosition, targetPosition)

                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                //Do nothing here
            }
        })

        touchHelper.attachToRecyclerView(recyclerView)
    }

    /**
     * Filters this adapter with a given search term and shows only the items which
     * matches it.
     *
     *
     * For the filter to work properly, each item must override the
     * [AdapterItem.onFilter] method and provide custom implementation.
     *
     * @param searchTerm search term
     */
    fun filter(searchTerm: String?) {
        if (itemsList.isEmpty()) {
            return
        }

        if (searchTerm.isNullOrBlank()) {
            showFiltered = false
            notifyDataSetChanged()
            return
        }

        filtered.apply {
            clear()
            addAll(itemsList.filter { it.onFilter(searchTerm) })
        }

        showFiltered = true
        notifyDataSetChanged()

    }

    /**
     * Sort items.
     *
     * each item must override the [AdapterItem.compareTo] method.
     *
     * With this method you can override the default [AdapterItem.compareTo] and use a
     * custom comparator which is responsible of item comparison.
     *
     * This is useful when your items has to be sorted with many different strategies
     * and not just one (e.g. order items by name, by date, ...).
     *
     * @param ascending  true for ascending order (A-Z) or false for descending order (Z-A).
     * Ascending order follows the passed comparator sorting algorithm order,
     * descending order uses the inverse order
     * @param comparator custom comparator implementation
     */
    @Suppress("UNCHECKED_CAST")
    fun sort(ascending: Boolean, comparator: Comparator<AdapterItem<*>>? = null) {
        val items = (items as ArrayList<AdapterItem<*>>)
                .takeIf { !it.isEmpty() }
                ?: return

        if (ascending) {
            if (comparator == null) {
                items.sort()
            } else {
                items.sortWith(comparator)
            }
        } else {
            if (comparator == null) {
                items.sortDescending()
            } else {
                Collections.reverseOrder(comparator)
            }
        }

        notifyDataSetChanged()
    }

    /**
     * Prevent RecyclerView from scrolling when adding many items
     * Taken from: https://github.com/airbnb/epoxy/issues/224#issuecomment-305991898
     *
     * @param layoutManager RecyclerView's Layout Manager
     */
    fun lockScrollingWhileInserting(layoutManager: RecyclerView.LayoutManager) {
        registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (positionStart == 0) {
                    layoutManager.scrollToPosition(0)
                }
            }
        })
    }
}
