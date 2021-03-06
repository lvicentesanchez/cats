package cats
package data

import cats.functor.Bifunctor

final case class WriterT[F[_], L, V](run: F[(L, V)]) {
  def written(implicit functorF: Functor[F]): F[L] =
    functorF.map(run)(_._1)

  def value(implicit functorF: Functor[F]): F[V] =
    functorF.map(run)(_._2)

  def ap[Z](f: WriterT[F, L, V => Z])(implicit F: Apply[F], L: Semigroup[L]): WriterT[F, L, Z] =
    WriterT(
      F.map2(f.run, run){
        case ((l1, fvz), (l2, v)) => (L.combine(l1, l2), fvz(v))
      })

  def map[Z](fn: V => Z)(implicit functorF: Functor[F]): WriterT[F, L, Z] =
    WriterT {
      functorF.map(run) { z => (z._1, fn(z._2)) }
    }

  def flatMap[U](f: V => WriterT[F, L, U])(implicit flatMapF: FlatMap[F], semigroupL: Semigroup[L]): WriterT[F, L, U] =
    WriterT {
      flatMapF.flatMap(run) { lv =>
        flatMapF.map(f(lv._2).run) { lv2 =>
          (semigroupL.combine(lv._1, lv2._1), lv2._2)
        }
      }
    }

  def mapBoth[M, U](f: (L, V) => (M, U))(implicit functorF: Functor[F]): WriterT[F, M, U] =
    WriterT { functorF.map(run)(f.tupled) }

  def bimap[M, U](f: L => M, g: V => U)(implicit functorF: Functor[F]): WriterT[F, M, U] =
    mapBoth((l, v) => (f(l), g(v)))

  def mapWritten[M](f: L => M)(implicit functorF: Functor[F]): WriterT[F, M, V] =
    mapBoth((l, v) => (f(l), v))

  def swap(implicit functorF: Functor[F]): WriterT[F, V, L] =
    mapBoth((l, v) => (v, l))

  def reset(implicit monoidL: Monoid[L], functorF: Functor[F]): WriterT[F, L, V] =
    mapWritten(_ => monoidL.empty)
}
object WriterT extends WriterTInstances with WriterTFunctions

private[data] sealed abstract class WriterTInstances extends WriterTInstances0 {

  implicit def writerTIdMonad[L:Monoid]: Monad[WriterT[Id, L, ?]] =
    writerTMonad[Id, L]

  // The Eq[(L, V)] can be derived from an Eq[L] and Eq[V], but we are waiting
  // on an algebra release that includes https://github.com/non/algebra/pull/82
  implicit def writerTIdEq[L, V](implicit E: Eq[(L, V)]): Eq[WriterT[Id, L, V]] =
    writerTEq[Id, L, V]

  implicit def writerTBifunctor[F[_]:Functor]: Bifunctor[WriterT[F, ?, ?]] =
    new Bifunctor[WriterT[F, ?, ?]] {
      def bimap[A, B, C, D](fab: WriterT[F, A, B])(f: A => C, g: B => D): WriterT[F, C, D] =
        fab.bimap(f, g)
    }
}

private[data] sealed abstract class WriterTInstances0 extends WriterTInstances1 {
  implicit def writerTMonad[F[_], L](implicit F: Monad[F], L: Monoid[L]): Monad[WriterT[F, L, ?]] =
    new WriterTMonad[F, L] {
      implicit val F0: Monad[F] = F
      implicit val L0: Monoid[L] = L
    }

  implicit def writerTIdFunctor[L]: Functor[WriterT[Id, L, ?]] =
    writerTFunctor[Id, L]

  implicit def writerTIdFlatMap[L:Semigroup]: FlatMap[WriterT[Id, L, ?]] =
    writerTFlatMap[Id, L]

  implicit def writerTEq[F[_], L, V](implicit F: Eq[F[(L, V)]]): Eq[WriterT[F, L, V]] =
    F.on(_.run)
}

private[data] sealed abstract class WriterTInstances1 extends WriterTInstances2 {
  implicit def writerTApplicative[F[_], L](implicit F: Applicative[F], L: Monoid[L]): Applicative[WriterT[F, L, ?]] =
    new WriterTApplicative[F, L] {
      implicit val F0: Applicative[F] = F
      implicit val L0: Monoid[L] = L
    }
}
private[data] sealed abstract class WriterTInstances2 extends WriterTInstances3 {
  implicit def writerTFlatMap[F[_], L](implicit F: FlatMap[F], L: Semigroup[L]): FlatMap[WriterT[F, L, ?]] =
    new WriterTFlatMap[F, L] {
      implicit val F0: FlatMap[F] = F
      implicit val L0: Semigroup[L] = L
    }
}

private[data] sealed abstract class WriterTInstances3 extends WriterTInstances4 {
  implicit def writerTApply[F[_], L](implicit F: Apply[F], L: Semigroup[L]): Apply[WriterT[F, L, ?]] =
    new WriterTApply[F, L] {
      implicit val F0: Apply[F] = F
      implicit val L0: Semigroup[L] = L
    }
}

private[data] sealed abstract class WriterTInstances4 {
  implicit def writerTFunctor[F[_], L](implicit F: Functor[F]): Functor[WriterT[F, L, ?]] = new WriterTFunctor[F, L] {
    implicit val F0: Functor[F] = F
  }
}

private[data] sealed trait WriterTFunctor[F[_], L] extends Functor[WriterT[F, L, ?]] {
  implicit def F0: Functor[F]

  def map[A, B](fa: WriterT[F, L, A])(f: A => B): WriterT[F, L, B] =
    fa.map(f)
}

private[data] sealed trait WriterTApply[F[_], L] extends WriterTFunctor[F, L] with Apply[WriterT[F, L, ?]] {
  override implicit def F0: Apply[F]
  implicit def L0: Semigroup[L]

  def ap[A, B](fa: WriterT[F, L, A])(f: WriterT[F, L, A => B]): WriterT[F, L, B] =
    fa ap f
}

private[data] sealed trait WriterTFlatMap[F[_], L] extends WriterTApply[F, L] with FlatMap[WriterT[F, L, ?]] {
  override implicit def F0: FlatMap[F]
  implicit def L0: Semigroup[L]

  def flatMap[A, B](fa: WriterT[F, L, A])(f: A => WriterT[F, L, B]): WriterT[F, L, B] =
    fa flatMap f
}

private[data] sealed trait WriterTApplicative[F[_], L] extends WriterTApply[F, L] with Applicative[WriterT[F, L, ?]] {
  override implicit def F0: Applicative[F]
  override implicit def L0: Monoid[L]

  def pure[A](a: A): WriterT[F, L, A] =
    WriterT.value[F, L, A](a)
}

private[data] sealed trait WriterTMonad[F[_], L] extends WriterTApplicative[F, L] with Monad[WriterT[F, L, ?]] {
  override implicit def F0: Monad[F]
  override implicit def L0: Monoid[L]

  def flatMap[A, B](fa: WriterT[F, L, A])(f: A => WriterT[F, L, B]): WriterT[F, L, B] =
    fa.flatMap(f)
}

trait WriterTFunctions {
  def putT[F[_], L, V](vf: F[V])(l: L)(implicit functorF: Functor[F]): WriterT[F, L, V] =
    WriterT(functorF.map(vf)(v => (l, v)))

  def put[F[_], L, V](v: V)(l: L)(implicit applicativeF: Applicative[F]): WriterT[F, L, V] =
    WriterT.putT[F, L, V](applicativeF.pure(v))(l)

  def tell[F[_], L](l: L)(implicit applicativeF: Applicative[F]): WriterT[F, L, Unit] =
    WriterT.put[F, L, Unit](())(l)

  def value[F[_], L, V](v: V)(implicit applicativeF: Applicative[F], monoidL: Monoid[L]): WriterT[F, L, V] =
    WriterT.put[F, L, V](v)(monoidL.empty)

  def valueT[F[_], L, V](vf: F[V])(implicit functorF: Functor[F], monoidL: Monoid[L]): WriterT[F, L, V] =
    WriterT.putT[F, L, V](vf)(monoidL.empty)
}


