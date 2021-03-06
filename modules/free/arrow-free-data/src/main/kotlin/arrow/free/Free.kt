package arrow.free

import arrow.Kind
import arrow.core.Either
import arrow.core.FunctionK
import arrow.core.identity
import arrow.higherkind
import arrow.typeclasses.Applicative
import arrow.typeclasses.Monad

fun <M, S, A> FreeOf<S, A>.foldMapK(f: FunctionK<S, M>, MM: Monad<M>): Kind<M, A> =
  (this as Free<S, A>).foldMap(f, MM)

@higherkind
sealed class Free<S, out A> : FreeOf<S, A> {

  companion object {
    fun <S, A> just(a: A): Free<S, A> = Pure(a)

    fun <S, A> liftF(fa: Kind<S, A>): Free<S, A> = Suspend(fa)

    fun <S, A> defer(value: () -> Free<S, A>): Free<S, A> = just<S, Unit>(Unit).flatMap { _ -> value() }

    internal fun <F> functionKF(): FunctionK<F, FreePartialOf<F>> =
      object : FunctionK<F, FreePartialOf<F>> {
        override fun <A> invoke(fa: Kind<F, A>): Free<F, A> =
          liftF(fa)

      }

    internal fun <F> applicativeF(applicative: Applicative<FreePartialOf<F>>): Applicative<FreePartialOf<F>> =
      object : Applicative<FreePartialOf<F>> {

        override fun <A> just(a: A): Free<F, A> =
          Companion.just(a)

        override fun <A, B> Kind<FreePartialOf<F>, A>.ap(ff: Kind<FreePartialOf<F>, (A) -> B>): Free<F, B> =
          applicative.run { ap(ff).fix() }
      }
  }

  abstract fun <O, B> transform(f: (A) -> B, fs: FunctionK<S, O>): Free<O, B>

  data class Pure<S, out A>(val a: A) : Free<S, A>() {
    override fun <O, B> transform(f: (A) -> B, fs: FunctionK<S, O>): Free<O, B> = just(f(a))
  }

  data class Suspend<S, out A>(val a: Kind<S, A>) : Free<S, A>() {
    override fun <O, B> transform(f: (A) -> B, fs: FunctionK<S, O>): Free<O, B> = liftF(fs(a)).map(f = f)
  }

  data class FlatMapped<S, out A, C>(val c: Free<S, C>, val fm: (C) -> Free<S, A>) : Free<S, A>() {
    override fun <O, B> transform(f: (A) -> B, fs: FunctionK<S, O>): Free<O, B> =
      FlatMapped(c.transform(::identity, fs)) { c.flatMap { fm(it) }.transform(f, fs) }
  }

  override fun toString(): String = "Free(...) : toString is not stack-safe"
}

fun <S, A, B> FreeOf<S, A>.map(f: (A) -> B): Free<S, B> = flatMap { Free.Pure<S, B>(f(it)) }

fun <S, A, B> FreeOf<S, A>.flatMap(f: (A) -> Free<S, B>): Free<S, B> = Free.FlatMapped(this.fix(), f)

fun <S, A, B> FreeOf<S, A>.ap(ff: FreeOf<S, (A) -> B>): Free<S, B> = ff.fix().flatMap { f -> map(f = f) }.fix()

/** Takes one evaluation step in the Free monad, re-associating left-nested binds in the process. */
@Suppress("UNCHECKED_CAST")
tailrec fun <S, A> Free<S, A>.step(): Free<S, A> =
  if (this is Free.FlatMapped<S, A, *> && this.c is Free.FlatMapped<S, *, *>) {
    val g = this.fm as (A) -> Free<S, A>
    val c = this.c.c as Free<S, A>
    val f = this.c.fm as (A) -> Free<S, A>
    c.flatMap { cc -> f(cc).flatMap(f = g) }.step()
  } else if (this is Free.FlatMapped<S, A, *> && this.c is Free.Pure<S, *>) {
    val a = this.c.a as A
    val f = this.fm as (A) -> Free<S, A>
    f(a).step()
  } else {
    this
  }

@Suppress("UNCHECKED_CAST")
fun <M, S, A> Free<S, A>.foldMap(f: FunctionK<S, M>, MM: Monad<M>): Kind<M, A> =
  MM.tailRecM(this@foldMap) {
    val x = it.step()
    when (x) {
      is Free.Pure<S, A> -> MM.just(Either.Right(x.a))
      is Free.Suspend<S, A> -> MM.run { f(x.a).map { Either.Right(it) } }
      is Free.FlatMapped<S, A, *> -> {
        val g = (x.fm as (A) -> Free<S, A>)
        val c = x.c as Free<S, A>
        val folded = c.foldMap(f, MM)
        MM.run { folded.map { cc -> Either.Left(g(cc)) } }
      }
    }
  }

fun <S, A> A.free(): Free<S, A> = Free.just<S, A>(this)

fun <F, A> Free<F, A>.run(M: Monad<F>): Kind<F, A> = this.foldMap(FunctionK.id(), M)

fun <F, A> FreeOf<F, A>.runK(M: Monad<F>): Kind<F, A> = this.fix().foldMap(FunctionK.id(), M)
