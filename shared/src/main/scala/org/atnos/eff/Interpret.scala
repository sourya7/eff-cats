package org.atnos.eff

import cats._
import cats.data._
import cats.implicits._
import Xor._
import Eff._

/**
 * Support methods to create interpreters (or "effect handlers") for a given effect M and a value Eff[R, A]
 * when M is a member of R.
 *
 * Those methods guarantee a stack-safe behaviour when running on a large list of effects
 * (in list.traverse(f) for example).
 *
 * There are different types of supported interpreters:
 *
 *  1. "interpret" + Recurse
 *
 *  This interpreter is used to handle effects which either return a value X from M[X] or stops with Eff[R, B]
 *  See an example of such an interpreter in Eval where we just evaluate a computation X for each Eval[X].
 *
 *  2. "interpretState" + StateRecurse
 *
 *  This interpreter is used to handle effects which either return a value X from M[X] or stops with Eff[R, B]
 *
 *  3. "interpretLoop" + Loop
 *
 *  The most generic kind of interpreter where we can even recurse in the case of Pure(a) (See ListEffect for such a use)
 *
 *  4. "intercept / interceptState / interceptLoop" methods are similar but they transform an effect to other effects in
 *  the same stack without removing it from the stack
 *
 *  5. "transform" to swap an effect T of a stack to another effect, using a Natural Transformation
 *
 *  6. "translate" to interpret one effect of a stack into other effects of the same stack using a Natural Transformation
 *     this is a specialized version of interpret + Recurse
 *
 *  7. "interpretUnsafe + SideEffect" when you have a side effecting function M[X] => X
 */
trait Interpret {

  /**
   * interpret the effect M in the R stack
   */
  def interpret[R, U, M[_], A, B](pure: A => Eff[U, B], recurse: Recurse[M, U, B])(effects: Eff[R, A])(implicit m: Member.Aux[M, R, U]): Eff[U, B] = {
    val loop = new Loop[M, R, A, Eff[U, B]] {
      type S = Unit
      val init = ()

      def onPure(a: A, s: Unit): (Eff[R, A], Unit) Xor Eff[U, B] =
        Right(pure(a))

      def onEffect[X](mx: M[X], continuation: Arrs[R, X, A], s: Unit): (Eff[R, A], Unit) Xor Eff[U, B] =
        recurse(mx).bimap(x => (continuation(x), ()), identity)

      def onApplicativeEffect[X](mx: List[M[X]], continuation: Arrs[R, List[X], A], s: Unit): (Eff[R, A], Unit) Xor Eff[U, B] =
        recurse.applicative(mx) match {
          case Xor.Left(xs) => Xor.Left((continuation(xs), s))
          case Xor.Right(mlx) => onEffect(mlx, continuation, s)
        }
    }
    interpretLoop[R, U, M, A, B](pure, loop)(effects)
  }

  /**
   * simpler version of interpret where the pure value is just mapped to another type
   */
  def interpret1[R, U, M[_], A, B](pure: A => B)(recurse: Recurse[M, U, B])(effects: Eff[R, A])(implicit m: Member.Aux[M, R, U]): Eff[U, B] =
    interpret[R, U, M, A, B]((a: A) => EffMonad[U].pure(pure(a)), recurse)(effects)

  /**
   * Helper trait for computations
   * which might produce several M[X] in a stack of effects and which need to keep some state around
   *
   * This is typically the case for Writer or State which need to keep some state S after each evaluation
   * Evaluating the effect M[X] might use the previous S value as shown in the `apply method`
   *
   * Finally when the Eff[R, A] returns an A, this one can be combined with the last state value to produce a B
   *
   */
  trait StateRecurse[M[_], A, B] {
    type S
    val init: S
    def apply[X](x: M[X], s: S): (X, S)
    def applicative[X](xs: List[M[X]], s: S): (List[X], S) Xor (M[List[X]], S)
    def finalize(a: A, s: S): B
  }

  /**
   * interpret the effect M in the M |: R stack, keeping track of some state
   */
  def interpretState[R, U, M[_], A, B](pure: A => Eff[U, B], recurse: StateRecurse[M, A, B])(effects: Eff[R, A])(implicit m: Member.Aux[M, R, U]): Eff[U, B] = {
    val loop = new Loop[M, R, A, Eff[U, B]] {
      type S = recurse.S
      val init: S = recurse.init

      def onPure(a: A, s: S): (Eff[R, A], S) Xor Eff[U, B] =
        Right(EffMonad[U].pure(recurse.finalize(a, s)))

      def onEffect[X](mx: M[X], continuation: Arrs[R, X, A], s: S): (Eff[R, A], S) Xor Eff[U, B] =
        Left { recurse(mx, s) match { case (a, b) => (continuation(a), b)} }

      def onApplicativeEffect[X](mx: List[M[X]], continuation: Arrs[R, List[X], A], s: S): (Eff[R, A], S) Xor Eff[U, B] =
        recurse.applicative(mx, s) match {
          case Xor.Left((ls, s1))   => Xor.Left((continuation(ls), s1))
          case Xor.Right((mlx, s1)) => onEffect(mlx, continuation, s1)
        }
    }
    interpretLoop(pure, loop)(effects)
  }

  /**
   * simpler version of interpret1 where the pure value is just mapped to another type
   */
  def interpretState1[R, U, M[_], A, B](pure: A => B)(recurse: StateRecurse[M, A, B])(effects: Eff[R, A])(implicit m: Member.Aux[M, R, U]): Eff[U, B] =
    interpretState((a: A) => EffMonad[U].pure(pure(a)), recurse)(effects)

  /**
   * generalization of interpret and interpretState
   *
   * This method contains a loop which is stack-safe
   */
  def interpretLoop[R, U, M[_], A, B](pure: A => Eff[U, B], loop: Loop[M, R, A, Eff[U, B]])(effects: Eff[R, A])(implicit m: Member.Aux[M, R, U]): Eff[U, B] = {
    def go(eff: Eff[R, A], s: loop.S): Eff[U, B] = {
      eff match {
        case Pure(a) =>
          loop.onPure(a, s) match {
            case Left((a1, s1)) => go(a1, s1)
            case Right(b) => b
          }

        case Impure(union, continuation) =>
          m.project(union) match {
            case Right(v) =>
              loop.onEffect(v, continuation, s) match {
                case Left((x, s1)) => go(x, s1)
                case Right(b)      => b
              }

            case Left(u) =>
              Impure[U, union.X, B](u, Arrs.singleton(x => go(continuation(x), s)))
          }

        case ap @ ImpureAp(unions, map) =>
          val collected = unions.project

          if (collected.effects.isEmpty)
            collected.othersEff(map).flatMap(pure)
          else
            loop.onApplicativeEffect(collected.effects, collected.continuation(map, m), s) match {
              case Left((x, s1)) => go(x, s1)
              case Right(b)      => b
            }
      }
    }

    go(effects, loop.init)
  }

  def interpretLoop1[R, U, M[_], A, B](pure: A => B)(loop: Loop[M, R, A, Eff[U, B]])(effects: Eff[R, A])(implicit m: Member.Aux[M, R, U]): Eff[U, B] =
    interpretLoop[R, U, M, A, B]((a: A) => EffMonad[U].pure(pure(a)), loop)(effects)

  /**
   * generalization of interpret
   *
   * This method contains a loop which is stack-safe
   */
  def interpretStatelessLoop[R, U, M[_], A, B](pure: A => Eff[U, B], loop: StatelessLoop[M, R, A, Eff[U, B]])(effects: Eff[R, A])(implicit m: Member.Aux[M, R, U]): Eff[U, B] =
    interpretLoop[R, U, M, A, B](pure, new Loop[M, R, A, Eff[U, B]] {
      type S = Unit
      val init: S = ()
      def onPure(a: A, s: S) = loop.onPure(a).leftMap((_, init))
      def onEffect[X](x: M[X], continuation: Arrs[R, X, A], s: S) = loop.onEffect(x, continuation).leftMap((_, init))
      def onApplicativeEffect[X](xs: List[M[X]], continuation: Arrs[R, List[X], A], s: S) = loop.onApplicativeEffect(xs, continuation).leftMap((_, init))
    })(effects)(m)

  def interpretStatelessLoop1[R, U, M[_], A, B](pure: A => B)(loop: StatelessLoop[M, R, A, Eff[U, B]])(effects: Eff[R, A])(implicit m: Member.Aux[M, R, U]): Eff[U, B] =
    interpretStatelessLoop[R, U, M, A, B]((a: A) => EffMonad[U].pure(pure(a)), loop)(effects)

  /**
   * INTERPRET IN THE SAME STACK
   */
  def intercept[R, M[_], A, B](pure: A => Eff[R, B], recurse: Recurse[M, R, B])(effects: Eff[R, A])(implicit m: M /= R): Eff[R, B] = {
    val loop = new Loop[M, R, A, Eff[R, B]] {
      type S = Unit
      val init = ()

      def onPure(a: A, s: Unit): (Eff[R, A], Unit) Xor Eff[R, B] =
        Right(pure(a))

      def onEffect[X](mx: M[X], continuation: Arrs[R, X, A], s: Unit): (Eff[R, A], Unit) Xor Eff[R, B] =
        recurse(mx).bimap(x => (continuation(x), ()), identity)

      def onApplicativeEffect[X](mx: List[M[X]], continuation: Arrs[R, List[X], A], s: S): (Eff[R, A], S) Xor Eff[R, B] =
        recurse.applicative(mx) match {
          case Xor.Left(ls)   => Xor.Left((continuation(ls), s))
          case Xor.Right(mlx) => onEffect(mlx, continuation, s)
        }
    }
    interceptLoop[R, M, A, B](pure, loop)(effects)
  }

  /**
   * simpler version of intercept where the pure value is just mapped to another type
   */
  def intercept1[R, M[_], A, B](pure: A => B)(recurse: Recurse[M, R, B])(effects: Eff[R, A])(implicit m: M /= R): Eff[R, B] =
    intercept[R, M, A, B]((a: A) => EffMonad[R].pure(pure(a)), recurse)(effects)

  /**
   * intercept an effect and interpret it in the same stack.
   * This method is stack-safe
   */
  def interceptLoop[R, M[_], A, B](pure: A => Eff[R, B], loop: Loop[M, R, A, Eff[R, B]])(effects: Eff[R, A])(implicit m: M /= R): Eff[R, B] = {
    def go(eff: Eff[R, A], s: loop.S): Eff[R, B] = {
      eff match {
        case Pure(a) =>
          loop.onPure(a, s) match {
            case Left((a1, s1)) => go(a1, s1)
            case Right(b) => b
          }

        case Impure(union, continuation) =>
          m.extract(union) match {
            case Some(v) =>
              loop.onEffect(v, continuation, s) match {
                case Left((x, s1)) => go(x, s1)
                case Right(b)      => b
              }

            case None =>
              Impure[R, union.X, B](union, Arrs.singleton(x => go(continuation(x), s)))
          }

        case ImpureAp(unions, map) =>
          val collect = unions.extract

          if (collect.effects.isEmpty)
            collect.othersEff(map).flatMap(pure)
          else
            loop.onApplicativeEffect(collect.effects, collect.continuation(map), s) match {
              case Left((x, s1)) => go(x, s1)
              case Right(b)      => b
            }
      }
    }

    go(effects, loop.init)
  }

  def interceptLoop1[R, M[_], A, B](pure: A => B)(loop: Loop[M, R, A, Eff[R, B]])(effects: Eff[R, A])(implicit m: M /= R): Eff[R, B] =
    interceptLoop[R, M, A, B]((a: A) => EffMonad[R].pure(pure(a)), loop)(effects)

  def interceptStatelessLoop[R, M[_], A, B](pure: A => Eff[R, B], loop: StatelessLoop[M, R, A, Eff[R, B]])(effects: Eff[R, A])(implicit m: M /= R): Eff[R, B] =
    interceptLoop[R, M, A, B](pure, new Loop[M, R, A, Eff[R, B]] {
      type S = Unit
      val init: S = ()
      def onPure(a: A, s: S) = loop.onPure(a).leftMap((_, ()))
      def onEffect[X](x: M[X], continuation: Arrs[R, X, A], s: S) = loop.onEffect(x, continuation).leftMap((_, ()))
      def onApplicativeEffect[X](xs: List[M[X]], continuation: Arrs[R, List[X], A], s: S) = loop.onApplicativeEffect(xs, continuation).leftMap((_, ()))
    })(effects)(m)

  def interceptStatelessLoop1[R, M[_], A, B](pure: A => B)(loop: StatelessLoop[M, R, A, Eff[R, B]])(effects: Eff[R, A])(implicit m: M /= R): Eff[R, B] =
    interceptStatelessLoop[R, M, A, B]((a: A) => EffMonad[R].pure(pure(a)), loop)(effects)

  /**
   * transform an effect into another one
   * using a natural transformation, leaving the rest of the stack untouched
   */
  def transform[SR, BR, U, TS[_], TB[_], A](r: Eff[SR, A], nat: TS ~> TB)
                                               (implicit sr: Member.Aux[TS, SR, U], br: Member.Aux[TB, BR, U]): Eff[BR, A] = {

    def go(eff: Eff[SR, A]): Eff[BR, A] = {
      eff match {
        case Pure(a) => Pure(a)

        case Impure(u, c) =>
          sr.project(u) match {
            case Xor.Right(small) =>
              Impure(br.inject(nat(small)), Arrs.singleton((x: u.X) => go(c(x))))

            case Xor.Left(u1) =>
              Impure(br.accept(u1), Arrs.singleton((x: u.X) => go(c(x))))
          }

        case ap @ ImpureAp(_,_) =>
          go(ap.toMonadic)
      }
    }

    go(r)
  }

  /**
   * Translate one effect of the stack into some of the other effects in the stack
   */
  def translate[R, U, T[_], A](effects: Eff[R, A])
                                                    (tr: Translate[T, U])
                                                    (implicit m: Member.Aux[T, R, U]): Eff[U, A] = {
    def go(eff: Eff[R, A]): Eff[U, A] = {
      eff match {
        case Pure(a) => Pure(a)

        case Impure(union, c) =>
          m.project(union) match {
            case Xor.Right(kv) =>
              val effectsU: Eff[U, union.X] = tr(kv)
              effectsU.flatMap(r => go(c(r)))

            case Xor.Left(u1) =>
              Impure(u1, Arrs.singleton((x: union.X) => go(c(x))))
          }

        case ap @ ImpureAp(unions, map) =>
          val collected = unions.project

          if (collected.effects.isEmpty)
            collected.othersEff(map)
          else {
            val translated: Eff[U, List[Any]] = EffApplicative.traverse(collected.effects)(tr.apply)
            translated.flatMap(ls => translate(collected.continuation(map, m).apply(ls))(tr))
          }
      }
    }

    go(effects)
  }

  /**
   * Translate one effect of the stack into some of the other effects in the stack
   * Using a natural transformation
   */
  def translateNat[R, U, T[_], A](effects: Eff[R, A])
                                 (nat: T ~> Eff[U, ?])
                                 (implicit m: Member.Aux[T, R, U]): Eff[U, A] =
    translate(effects)(new Translate[T, U] {
      def apply[X](tx: T[X]): Eff[U, X] = nat(tx)
    })

  /** interpret an effect by running side-effects */
  def interpretUnsafe[R, U, T[_], A](effects: Eff[R, A])
                                                          (sideEffect: SideEffect[T])
                                                          (implicit m: Member.Aux[T, R, U]): Eff[U, A] = {
    val recurse = new Recurse[T, m.Out, A] {
      def apply[X](tx: T[X]): X Xor Eff[m.Out, A] =
        Xor.left(sideEffect(tx))

      def applicative[X](ms: List[T[X]]): List[X] Xor T[List[X]]=
        Xor.Left(ms.map(sideEffect.apply))
    }
    interpret1((a: A) => a)(recurse)(effects)(m)
  }

  /**
   * Helper trait for computations
   * which might produce several M[X] in a stack of effects.
   *
   * Either we can produce an X to pass to a continuation or we're done
   */
  trait Recurse[M[_], R, A] {
    def apply[X](m: M[X]): X Xor Eff[R, A]
    def applicative[X](ms: List[M[X]]): List[X] Xor M[List[X]]
  }

  /**
   * Generalisation of Recurse and StateRecurse
   */
  trait Loop[M[_], R, A, B] {
    type S
    val init: S
    def onPure(a: A, s: S): (Eff[R, A], S) Xor B
    def onEffect[X](x: M[X], continuation: Arrs[R, X, A], s: S): (Eff[R, A], S) Xor B
    def onApplicativeEffect[X](xs: List[M[X]], continuation: Arrs[R, List[X], A], s: S): (Eff[R, A], S) Xor B
  }

  /**
   * Generalisation of Recurse
   */
  trait StatelessLoop[M[_], R, A, B] {
    def onPure(a: A): Eff[R, A] Xor B
    def onEffect[X](x: M[X], continuation: Arrs[R, X, A]): Eff[R, A] Xor B
    def onApplicativeEffect[X](xs: List[M[X]], continuation: Arrs[R, List[X], A]): Eff[R, A] Xor B
  }

  /**
   * trait for translating one effect into other ones in the same stack
   */
  trait Translate[T[_], U] {
    def apply[X](kv: T[X]): Eff[U, X]
  }

  trait SideEffect[T[_]] {
    def apply[X](tx: T[X]): X
    def applicative[X](ms: List[T[X]]): List[X]
  }

}

object Interpret extends Interpret

