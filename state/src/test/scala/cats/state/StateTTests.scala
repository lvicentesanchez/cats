package cats
package state

import cats.tests.CatsSuite
import cats.laws.discipline.{MonadStateTests, MonoidKTests, SerializableTests}
import cats.laws.discipline.eq._
import org.scalacheck.{Arbitrary, Gen}

class StateTTests extends CatsSuite {
  import StateTTests._

  test("basic state usage"){
    add1.run(1).run should === (2 -> 1)
  }

  test("traversing state is stack-safe"){
    val ns = (0 to 100000).toList
    val x = ns.traverseU(_ => add1)
    x.runS(0).run should === (100001)
  }

  test("State.pure and StateT.pure are consistent"){
    forAll { (s: String, i: Int) =>
      val state: State[String, Int] = State.pure(i)
      val stateT: State[String, Int] = StateT.pure(i)
      state.run(s).run should === (stateT.run(s).run)
    }
  }

  test("Apply syntax is usable on State") {
    val x = add1 *> add1
    x.runS(0).run should === (2)
  }

  test("Singleton and instance inspect are consistent"){
    forAll { (s: String, i: Int) =>
      State.inspect[Int, String](_.toString).run(i).run should === (
        State.pure[Int, Unit](()).inspect(_.toString).run(i).run)
    }
  }

  checkAll("StateT[Option, Int, Int]", MonadStateTests[StateT[Option, Int, ?], Int].monadState[Int, Int, Int])
  checkAll("MonadState[StateT[Option, ?, ?], Int]", SerializableTests.serializable(MonadState[StateT[Option, Int, ?], Int]))
}

object StateTTests {

  implicit def stateArbitrary[F[_]: Applicative, S, A](implicit F: Arbitrary[S => F[(S, A)]]): Arbitrary[StateT[F, S, A]] =
    Arbitrary(F.arbitrary.map(f => StateT(f)))

  implicit def stateEq[F[_], S, A](implicit S: Arbitrary[S], FSA: Eq[F[(S, A)]], F: FlatMap[F]): Eq[StateT[F, S, A]] =
    Eq.by[StateT[F, S, A], S => F[(S, A)]](state =>
      s => state.run(s))

  val add1: State[Int, Int] = State(n => (n + 1, n))
}
