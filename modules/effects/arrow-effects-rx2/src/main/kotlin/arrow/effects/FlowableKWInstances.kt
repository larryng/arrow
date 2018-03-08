package arrow.effects

import arrow.core.Either
import arrow.effects.continuations.EffectContinuation
import arrow.instance
import arrow.typeclasses.ApplicativeError
import arrow.typeclasses.MonadError
import arrow.typeclasses.continuations.BindingCatchContinuation
import arrow.typeclasses.continuations.BindingContinuation
import io.reactivex.BackpressureStrategy
import kotlin.coroutines.experimental.CoroutineContext

@instance(FlowableK::class)
interface FlowableKApplicativeErrorInstance :
        FlowableKApplicativeInstance,
        ApplicativeError<ForFlowableK, Throwable> {
    override fun <A> raiseError(e: Throwable): FlowableK<A> =
            FlowableK.raiseError(e)

    override fun <A> handleErrorWith(fa: FlowableKOf<A>, f: (Throwable) -> FlowableKOf<A>): FlowableK<A> =
            fa.handleErrorWith { f(it).fix() }
}

@instance(FlowableK::class)
interface FlowableKMonadInstance : arrow.typeclasses.Monad<ForFlowableK> {
    override fun <A, B> ap(fa: arrow.effects.FlowableKOf<A>, ff: arrow.effects.FlowableKOf<kotlin.Function1<A, B>>): arrow.effects.FlowableK<B> =
            fa.fix().ap(ff)

    override fun <A, B> flatMap(fa: arrow.effects.FlowableKOf<A>, f: kotlin.Function1<A, arrow.effects.FlowableKOf<B>>): arrow.effects.FlowableK<B> =
            fa.fix().flatMap(f)

    override fun <A, B> map(fa: arrow.effects.FlowableKOf<A>, f: kotlin.Function1<A, B>): arrow.effects.FlowableK<B> =
            fa.fix().map(f)

    override fun <A, B> tailRecM(a: A, f: kotlin.Function1<A, arrow.effects.FlowableKOf<arrow.core.Either<A, B>>>): arrow.effects.FlowableK<B> =
            arrow.effects.FlowableK.tailRecM(a, f)

    override fun <A> pure(a: A): arrow.effects.FlowableK<A> =
            arrow.effects.FlowableK.pure(a)

    override fun <B> binding(cc: CoroutineContext, c: suspend BindingContinuation<ForFlowableK, *>.() -> B): FlowableK<B> =
            EffectContinuation.bindingIn(FlowableK.effect(), cc, c).fix()
}

@instance(FlowableK::class)
interface FlowableKMonadErrorInstance :
        FlowableKApplicativeErrorInstance,
        FlowableKMonadInstance,
        MonadError<ForFlowableK, Throwable> {
    override fun <A, B> ap(fa: FlowableKOf<A>, ff: FlowableKOf<(A) -> B>): FlowableK<B> =
            super<FlowableKMonadInstance>.ap(fa, ff)

    override fun <A, B> map(fa: FlowableKOf<A>, f: (A) -> B): FlowableK<B> =
            super<FlowableKMonadInstance>.map(fa, f)

    override fun <A> pure(a: A): FlowableK<A> =
            super<FlowableKMonadInstance>.pure(a)

    override fun <B> bindingCatch(cc: CoroutineContext, catch: (Throwable) -> Throwable, c: suspend BindingCatchContinuation<ForFlowableK, Throwable, *>.() -> B): FlowableK<B> =
            EffectContinuation.bindingCatchIn(FlowableK.effect(), catch, cc, c).fix()
}

@instance(FlowableK::class)
interface FlowableKMonadSuspendInstance :
        FlowableKMonadErrorInstance,
        MonadSuspend<ForFlowableK> {
    override fun <A> suspend(fa: () -> FlowableKOf<A>): FlowableK<A> =
            FlowableK.suspend(fa)

    fun BS(): BackpressureStrategy = BackpressureStrategy.BUFFER
}

@instance(FlowableK::class)
interface FlowableKAsyncInstance :
        FlowableKMonadSuspendInstance,
        Async<ForFlowableK> {
    override fun <A> async(fa: Proc<A>): FlowableK<A> =
            FlowableK.async(fa, BS())
}

@instance(FlowableK::class)
interface FlowableKEffectInstance :
        FlowableKAsyncInstance,
        Effect<ForFlowableK> {
    override fun <A> runAsync(fa: FlowableKOf<A>, cb: (Either<Throwable, A>) -> FlowableKOf<Unit>): FlowableK<Unit> =
            fa.fix().runAsync(cb)
}