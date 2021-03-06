package org.atnos.eff

import cats.data.Xor
import Eff._

/**
 * Typeclass proving that it is possible to send a tree of effects R into another tree of effects U
 *
 * for example
 *
 *  send[Option1, Fx.fx3[Option1, Option2, Option3], Int](Option1(1)).
 *    into[Fx.fx5[Option1, Option2, Option3, Option4, Option5]]
 *
 *  should work because all the effects of the first stack are present in the second
 *
 * Note: some implicit definitions are probably missing in some cases
 */
trait IntoPoly[R, U] {
  def apply[A](e: Eff[R, A]): Eff[U, A]
}

object IntoPoly extends IntoPolyLower1

trait IntoPolyLower1 extends IntoPolyLower2 {

  implicit def intoNil[R]: IntoPoly[NoFx, R] =
    new IntoPoly[NoFx, R] {
      def apply[A](e: Eff[NoFx, A]) =
        e match {
          case Pure(a) => pure[R, A](a)
          case _ => sys.error("impossible NoFx into R is only for pure values")
        }
    }

  implicit def intoSelf[R]: IntoPoly[R, R] =
    new IntoPoly[R, R] { def apply[A](e: Eff[R, A]) = e }

}

trait IntoPolyLower2  extends IntoPolyLower3 {

  implicit def intoAppendL2L[T1[_], T2[_], R]: IntoPoly[FxAppend[Fx1[T2], R], FxAppend[Fx2[T1, T2], R]] =
    new IntoPoly[FxAppend[Fx1[T2], R], FxAppend[Fx2[T1, T2], R]] {
      def apply[A](e: Eff[FxAppend[Fx1[T2], R], A]): Eff[FxAppend[Fx2[T1, T2], R], A] =
        e match {
          case Pure(a) =>
            EffMonad[FxAppend[Fx2[T1, T2], R]].pure(a)

          case Impure(u@UnionAppendR(r), c) =>
            Impure[FxAppend[Fx2[T1, T2], R], u.X, A](UnionAppendR(r), Arrs.singleton(x => effInto(c(x))))

          case Impure(u@UnionAppendL(Union1(tx)), c) =>
            Impure[FxAppend[Fx2[T1, T2], R], u.X, A](UnionAppendL(Union2R(tx)), Arrs.singleton(x => effInto(c(x))))

          case ImpureAp(unions, map) =>
            ImpureAp[FxAppend[Fx2[T1, T2], R], unions.X, A](
              unions.into(new UnionInto[FxAppend[Fx1[T2], R], FxAppend[Fx2[T1, T2], R]] {
                def apply[X](union: Union[FxAppend[Fx1[T2], R], X]) = union match {
                  case UnionAppendR(r) => UnionAppendR(r)
                  case UnionAppendL(Union1(tx)) => UnionAppendL(Union2R(tx))
                }}), map)
        }
    }

  implicit def intoAppendL2R[T1[_], T2[_], R]: IntoPoly[FxAppend[Fx1[T1], R], FxAppend[Fx2[T1, T2], R]] =
    new IntoPoly[FxAppend[Fx1[T1], R], FxAppend[Fx2[T1, T2], R]] {
      def apply[A](e: Eff[FxAppend[Fx1[T1], R], A]): Eff[FxAppend[Fx2[T1, T2], R], A] =
        e match {
          case Pure(a) =>
            EffMonad[FxAppend[Fx2[T1, T2], R]].pure(a)

          case Impure(u@UnionAppendR(r), c) =>
            Impure[FxAppend[Fx2[T1, T2], R], u.X, A](UnionAppendR(r), Arrs.singleton(x => effInto(c(x))))

          case Impure(u@UnionAppendL(Union1(tx)), c) =>
            Impure[FxAppend[Fx2[T1, T2], R], u.X, A](UnionAppendL(Union2L(tx)), Arrs.singleton(x => effInto(c(x))))

          case ImpureAp(unions, map) =>
            ImpureAp[FxAppend[Fx2[T1, T2], R], unions.X, A](
              unions.into(new UnionInto[FxAppend[Fx1[T1], R], FxAppend[Fx2[T1, T2], R]] {
                def apply[X](union: Union[FxAppend[Fx1[T1], R], X]) = union match {
                  case UnionAppendR(r) => UnionAppendR(r)
                  case UnionAppendL(Union1(tx)) => UnionAppendL(Union2L(tx))
                }}), map)
        }
    }

  implicit def intoAppendL3L[T1[_], T2[_], T3[_], R]: IntoPoly[FxAppend[Fx2[T2, T3], R], FxAppend[Fx3[T1, T2, T3], R]] =
    new IntoPoly[FxAppend[Fx2[T2, T3], R], FxAppend[Fx3[T1, T2, T3], R]] {
      def apply[A](e: Eff[FxAppend[Fx2[T2, T3], R], A]): Eff[FxAppend[Fx3[T1, T2, T3], R], A] =
        e match {
          case Pure(a) =>
            EffMonad[FxAppend[Fx3[T1, T2, T3], R]].pure(a)

          case Impure(u@UnionAppendR(r), c) =>
            Impure[FxAppend[Fx3[T1, T2, T3], R], u.X, A](UnionAppendR(r), Arrs.singleton(x => effInto(c(x))))

          case Impure(u@UnionAppendL(Union2L(tx)), c) =>
            Impure[FxAppend[Fx3[T1, T2, T3], R], u.X, A](UnionAppendL(Union3M(tx)), Arrs.singleton(x => effInto(c(x))))

          case Impure(u@UnionAppendL(Union2R(tx)), c) =>
            Impure[FxAppend[Fx3[T1, T2, T3], R], u.X, A](UnionAppendL(Union3R(tx)), Arrs.singleton(x => effInto(c(x))))

          case ImpureAp(unions, map) =>
            ImpureAp[FxAppend[Fx3[T1, T2, T3], R], unions.X, A](
              unions.into(new UnionInto[FxAppend[Fx2[T2, T3], R], FxAppend[Fx3[T1, T2, T3], R]] {
                def apply[X](union: Union[FxAppend[Fx2[T2, T3], R], X]) = union match {
                  case UnionAppendR(r) => UnionAppendR(r)
                  case UnionAppendL(Union2L(tx)) => UnionAppendL(Union3M(tx))
                  case UnionAppendL(Union2R(tx)) => UnionAppendL(Union3R(tx))
                }}), map)
        }
    }

  implicit def intoAppendL3M[T1[_], T2[_], T3[_], R]: IntoPoly[FxAppend[Fx2[T1, T3], R], FxAppend[Fx3[T1, T2, T3], R]] =
    new IntoPoly[FxAppend[Fx2[T1, T3], R], FxAppend[Fx3[T1, T2, T3], R]] {
      def apply[A](e: Eff[FxAppend[Fx2[T1, T3], R], A]): Eff[FxAppend[Fx3[T1, T2, T3], R], A] =
        e match {
          case Pure(a) =>
            EffMonad[FxAppend[Fx3[T1, T2, T3], R]].pure(a)

          case Impure(u@UnionAppendR(r), c) =>
            Impure[FxAppend[Fx3[T1, T2, T3], R], u.X, A](UnionAppendR(r), Arrs.singleton(x => effInto(c(x))))

          case Impure(u@UnionAppendL(Union2L(tx)), c) =>
            Impure[FxAppend[Fx3[T1, T2, T3], R], u.X, A](UnionAppendL(Union3L(tx)), Arrs.singleton(x => effInto(c(x))))

          case Impure(u@UnionAppendL(Union2R(tx)), c) =>
            Impure[FxAppend[Fx3[T1, T2, T3], R], u.X, A](UnionAppendL(Union3R(tx)), Arrs.singleton(x => effInto(c(x))))

          case ImpureAp(unions, map) =>
            ImpureAp[FxAppend[Fx3[T1, T2, T3], R], unions.X, A](
              unions.into(new UnionInto[FxAppend[Fx2[T1, T3], R], FxAppend[Fx3[T1, T2, T3], R]] {
                def apply[X](union: Union[FxAppend[Fx2[T1, T3], R], X]) = union match {
                  case UnionAppendR(r) => UnionAppendR(r)
                  case UnionAppendL(Union2L(tx)) => UnionAppendL(Union3L(tx))
                  case UnionAppendL(Union2R(tx)) => UnionAppendL(Union3R(tx))
                }}), map)
        }
    }

  implicit def intoAppendL3R[T1[_], T2[_], T3[_], R]: IntoPoly[FxAppend[Fx2[T1, T2], R], FxAppend[Fx3[T1, T2, T3], R]] =
    new IntoPoly[FxAppend[Fx2[T1, T2], R], FxAppend[Fx3[T1, T2, T3], R]] {
      def apply[A](e: Eff[FxAppend[Fx2[T1, T2], R], A]): Eff[FxAppend[Fx3[T1, T2, T3], R], A] =
        e match {
          case Pure(a) =>
            EffMonad[FxAppend[Fx3[T1, T2, T3], R]].pure(a)

          case Impure(u@UnionAppendR(r), c) =>
            Impure[FxAppend[Fx3[T1, T2, T3], R], u.X, A](UnionAppendR(r), Arrs.singleton(x => effInto(c(x))))

          case Impure(u@UnionAppendL(Union2L(tx)), c) =>
            Impure[FxAppend[Fx3[T1, T2, T3], R], u.X, A](UnionAppendL(Union3L(tx)), Arrs.singleton(x => effInto(c(x))))

          case Impure(u@UnionAppendL(Union2R(tx)), c) =>
            Impure[FxAppend[Fx3[T1, T2, T3], R], u.X, A](UnionAppendL(Union3M(tx)), Arrs.singleton(x => effInto(c(x))))

          case ImpureAp(unions, map) =>
            ImpureAp[FxAppend[Fx3[T1, T2, T3], R], unions.X, A](
              unions.into(new UnionInto[FxAppend[Fx2[T1, T2], R], FxAppend[Fx3[T1, T2, T3], R]] {
                def apply[X](union: Union[FxAppend[Fx2[T1, T2], R], X]) = union match {
                  case UnionAppendR(r) => UnionAppendR(r)
                  case UnionAppendL(Union2L(tx)) => UnionAppendL(Union3L(tx))
                  case UnionAppendL(Union2R(tx)) => UnionAppendL(Union3M(tx))
                }}), map)

        }
    }
}

trait IntoPolyLower3 extends IntoPolyLower4 {
  implicit def intoAppendL1[T[_], R]: IntoPoly[R, FxAppend[Fx1[T], R]] =
    new IntoPoly[R, FxAppend[Fx1[T], R]] {
      def apply[A](e: Eff[R, A]): Eff[FxAppend[Fx1[T], R], A] =
        e match {
          case Pure(a) =>
            EffMonad[FxAppend[Fx1[T], R]].pure(a)

          case Impure(u, c) =>
            Impure[FxAppend[Fx1[T], R], u.X, A](UnionAppendR(u), Arrs.singleton(x => effInto(c(x))))

          case ImpureAp(unions, map) =>
            ImpureAp[FxAppend[Fx1[T], R], unions.X, A](
              unions.into(new UnionInto[R, FxAppend[Fx1[T], R]] {
                def apply[X](union: Union[R, X]) = union match {
                  case u => UnionAppendR(u)
                }}), map)
        }
    }
}

trait IntoPolyLower4 {

  implicit def into[T[_], R, Q, U, S](implicit
                                      t: Member.Aux[T, R, S],
                                      m: T |= U,
                                      recurse: IntoPoly[S, U]): IntoPoly[R, U] =
    new IntoPoly[R, U] {
      def apply[A](e: Eff[R, A]): Eff[U, A] =
        e match {
          case Pure(a) =>
            EffMonad[U].pure(a)

          case Impure(u, c) =>
            t.project(u) match {
              case Xor.Right(tx) => Impure[U, u.X, A](m.inject(tx), Arrs.singleton(x => effInto(c(x))))
              case Xor.Left(s)   => recurse(Impure[S, s.X, A](s, c.asInstanceOf[Arrs[S, s.X, A]]))
            }

          case ImpureAp(unions, map) =>
            ImpureAp[U, unions.X, A](unions.into(new UnionInto[R, U] {
              def apply[X](u: Union[R, X]): Union[U, X] =
                t.project(u) match {
                  case Xor.Right(t1)   => m.inject(t1)
                  case Xor.Left(other) => recurse(Impure[S, X, X](other, Arrs.singleton((x: X) => pure[S, X](x)))).asInstanceOf[Impure[U, X, X]].union
                }
            }), map)
        }
    }
}

