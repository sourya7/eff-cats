package org.atnos.eff

import Eff._
import scala.collection.mutable.ListBuffer
import cats.data._, Xor._
import Interpret._

/**
 * Effect for computations possibly returning several values
 */
trait ListEffect extends
  ListCreation with
  ListInterpretation

object ListEffect extends ListEffect

trait ListCreation {

  type _List[R] = List <= R
  type _list[R] = List |= R

  /** create a list effect with no values */
  def empty[R :_list, A]: Eff[R, A] =
    fromList(List())

  /** create a list effect from a single value */
  def singleton[R :_list, A](a: A): Eff[R, A] =
    fromList(List(a))

  /** create a list effect from a list of values */
  def values[R :_list, A](as: A*): Eff[R, A] =
    fromList(as.toList)

  /** create a list effect from a list of values */
  def fromList[R :_list, A](as: List[A]): Eff[R, A] =
    send[List, R, A](as)
}

object ListCreation extends ListCreation

trait ListInterpretation {
  /** run an effect stack starting with a list effect */
  def runList[R, U, A](effects: Eff[R, A])(implicit m: Member.Aux[List, R, U]): Eff[U, List[A]] = {
    val loop = new Loop[List, R, A, Eff[U, List[A]]] {
      type S = (List[Eff[R, A]], ListBuffer[A])
      val init = (List[Eff[R, A]](), new ListBuffer[A])

      def onPure(a: A, s: S): (Eff[R, A], S) Xor Eff[U, List[A]] =
        s match {
          case (head :: tail, result) => Left((head, (tail, result :+ a)))
          case (List(), result)       => Right(EffMonad[U].pure((result :+ a).toList))
        }

      def onEffect[X](l: List[X], continuation: Arrs[R, X, A], s: S): (Eff[R, A], S) Xor Eff[U, List[A]] =
        (l, s) match {
          case (List(), (head :: tail, result)) =>
            Left((head, (tail, result)))

          case (List(), (List(), result)) =>
            Right(EffMonad[U].pure(result.toList))

          case (head :: tail, (unevaluated, result)) =>
            Left((continuation(head), (tail.map(a => continuation(a)) ++ unevaluated, result)))
        }

      def onApplicativeEffect[X](xs: List[List[X]], continuation: Arrs[R, List[X], A], s: S): (Eff[R, A], S) Xor Eff[U, List[A]] = {
        if (xs.exists(_.isEmpty))
          s match {
            case (Nil, as)       => Xor.Right(pure(as.toList))
            case (e :: rest, as) => Xor.Left((e, (rest, as)))
          }

        else {
          xs.transpose.map(ls => continuation(ls)) match {
            case Nil =>
              s match {
                case (Nil, as)       => Xor.Right(pure(as.toList))
                case (e :: rest, as) => Xor.Left((e, (rest, as)))
              }
             case x :: rest =>
               Xor.Left((x, (rest ++ s._1, s._2)))
          }
        }
      }

    }

    interpretLoop1((a: A) => List(a))(loop)(effects)
  }
}

object ListInterpretation extends ListInterpretation
