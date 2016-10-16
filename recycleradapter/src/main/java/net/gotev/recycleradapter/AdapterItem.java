package net.gotev.recycleradapter;

import android.os.Bundle;
import android.view.View;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;

/**
 * Abstract class to extend to create ViewHolders.
 * @author Aleksandar Gotev
 * @param <T> ViewHolder subclass
 */
public abstract class AdapterItem<T extends RecyclerAdapterViewHolder> {

    /**
     * Gets called by {@link RecyclerAdapter#notifyItemChanged(RecyclerAdapterViewHolder, Bundle)}
     * @param dataChanged bunde containing the model data that has been changed
     */
    public abstract void onItemChanged(Bundle dataChanged);

    /**
     * Creates a new ViewHolder instance, by inferring the ViewHolder type from the generic passed
     * to this class
     * @param view View to be passed to the ViewHolder
     * @param adapter {@link RecyclerAdapter} instance
     * @return ViewHolder
     * @throws NoSuchMethodException if no mathing constructor are found in the ViewHolder subclass
     * @throws InstantiationException if an error happens during instantiation of the ViewHolder subclass
     * @throws InvocationTargetException if an error happens during a method invocation of the ViewHolder subclass
     * @throws IllegalAccessException if a method, field or class has been declared with insufficient access control modifiers
     */
    @SuppressWarnings("unchecked")
    final T getViewHolder(View view, RecyclerAdapterNotifier adapter)
            throws NoSuchMethodException, InstantiationException,
            InvocationTargetException, IllegalAccessException {

        // find the class of the generic type passed, to be able to get a new instance
        // it it less performant than passing Class<T> directly in the costructor, but avoids
        // writing additional code, making the subclasses stay lighter
        Class<T> clazz = (Class<T>) ((ParameterizedType)getClass()
                .getGenericSuperclass())
                .getActualTypeArguments()[0];

        return clazz.getConstructor(View.class, RecyclerAdapterNotifier.class)
                .newInstance(view, adapter);
    }

    /**
     * Returns the layout ID for this item
     * @return layout ID
     */
    public abstract int getLayoutId();

    /**
     * Bind the current item with the view
     * @param holder ViewHolder on which to bind data
     */
    protected abstract void bind(T holder);
}