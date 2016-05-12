package org.atnos.eff.syntax

import cats.Monoid
import cats.data._
import org.atnos.eff.Tag._
import org.atnos.eff._

object state extends state

trait state {

  implicit class StateEffectOps[R <: Effects, A](e: Eff[R, A]) {

    def runState[S](s: S)(implicit member: Member[State[S, ?], R]): Eff[member.Out, (A, S)] =
      StateInterpretation.runState(s)(e)(member.aux)

    def runStateTagged[S, T](s: S)(implicit member: Member[({type l[X] = State[S, X] @@ T})#l, R]): Eff[member.Out, (A, S)] =
      StateInterpretation.runStateTagged(s)(e)(member.aux)

    def runStateZero[S : Monoid](implicit member: Member[State[S, ?], R]): Eff[member.Out, (A, S)] =
      StateInterpretation.runStateZero(e)(Monoid[S], member.aux)

    def evalState[S](s: S)(implicit member: Member[State[S, ?], R]): Eff[member.Out, A] =
      StateInterpretation.evalState(s)(e)(member.aux)

    def evalStateTagged[S, T](s: S)(implicit member: Member[({type l[X] = State[S, X] @@ T})#l, R]): Eff[member.Out, A] =
      StateInterpretation.evalStateTagged(s)(e)(member.aux)

    def evalStateZero[S : Monoid](implicit member: Member[State[S, ?], R]): Eff[member.Out, A] =
      StateInterpretation.evalStateZero(e)(Monoid[S], member.aux)

    def execState[S](s: S)(implicit member: Member[State[S, ?], R]): Eff[member.Out, S] =
      StateInterpretation.execState(s)(e)(member.aux)

    def execStateZero[S : Monoid](implicit member: Member[State[S, ?], R]): Eff[member.Out, S] =
      StateInterpretation.execStateZero(e)(Monoid[S], member.aux)

    def execStateTagged[S, T](s: S)(implicit member: Member[({type l[X] = State[S, X] @@ T})#l, R]): Eff[member.Out, S] =
      StateInterpretation.execStateTagged(s)(e)(member.aux)

    def lensState[BR, U <: Effects, T, S](getter: S => T, setter: (S, T) => S)(implicit m1: Member.Aux[State[T, ?], R, U], m2: Member.Aux[State[S, ?], BR, U]): Eff[BR, A] =
      StateInterpretation.lensState[R, BR, U, T, S, A](e, getter, setter)

  }

}



