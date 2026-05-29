package poeregex.ui

import eu.nitonfx.signaling.api.Context
import eu.nitonfx.signaling.api.EffectHandle
import eu.nitonfx.signaling.api.ListSignal
import eu.nitonfx.signaling.api.MapSignal
import eu.nitonfx.signaling.api.SetSignal
import eu.nitonfx.signaling.api.Signal
import eu.nitonfx.signaling.api.SignalLike
import java.util.function.Consumer
import java.util.function.Supplier

class ThreadSaveSignalingContext(
    contextFactory: Supplier<Context>
) : Context {
    private val threadedContext = ThreadLocal.withInitial(contextFactory)
    private val context: Context get() = threadedContext.get()
    override fun <T> createSignal(initial: T?): Signal<T?>? = context.createSignal(initial)

    override fun <T> createNullSignal(): Signal<T?>? = context.createNullSignal()

    override fun <T> createSignal(initial: List<T?>?): ListSignal<T?>? = context.createSignal(initial)

    override fun <T> createSignal(initial: Array<out T?>?): ListSignal<T?>? = context.createSignal(initial)

    override fun <T> createListSignal(): ListSignal<T?>? = context.createListSignal()

    override fun <T> createSignal(initial: Set<T?>?): SetSignal<T?>? = context.createSignal(initial)

    override fun <T> createSetSignal(): SetSignal<T?>? = context.createSetSignal()

    override fun <K, V> createSignal(initial: Map<K?, V?>?): MapSignal<K?, V?>? = context.createSignal(initial)

    override fun <K, V> createMapSignal(): MapSignal<K?, V?>? = context.createMapSignal()

    override fun <T> createMemo(function: Supplier<T?>): SignalLike<T?>? = context.createMemo(function)

    override fun <T> untracked(function: Supplier<T?>): T? = context.untracked(function)

    override fun createEffect(effect: Runnable?): EffectHandle? = context.createEffect(effect)

    override fun cleanup(o: Runnable?) = context.cleanup(o)

    override fun untracked(effect: Runnable?) = context.untracked(effect)

    override fun run(effect: Runnable?): EffectHandle? = context.run(effect)

    override fun setPostEffectExecutionHook(hook: Consumer<EffectHandle?>?) = context.setPostEffectExecutionHook(hook)

    override fun registerEffect(customEffect: EffectHandle?) = context.registerEffect(customEffect)
}