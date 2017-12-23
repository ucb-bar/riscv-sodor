// See LICENSE.SiFive for license details.

package unittest

import Chisel._
import config._
import util.SimpleTimer

trait HasUnitTestIO {
  val io = new Bundle {
    val finished = Bool(OUTPUT)
    val start = Bool(INPUT)
  }
}

abstract class UnitTest(val timeout: Int = 4096) extends Module with HasUnitTestIO {
  val testName = this.getClass.getSimpleName

  when (io.start) { printf(s"Started UnitTest $testName\n") }

  val timed_out = SimpleTimer(timeout, io.start, io.finished)
  assert(!timed_out, s"UnitTest $testName timed out")
}

case object UnitTests extends Field[Parameters => Seq[UnitTest]]

class UnitTestSuite(implicit p: Parameters) extends Module {
  val io = new Bundle {
    val finished = Bool(OUTPUT)
  }

  val tests = p(UnitTests)(p)

  val s_idle :: s_start :: s_busy :: s_done :: Nil = Enum(Bits(), 4)
  val state = Reg(init = s_idle)
  val tests_finished = Vec(tests.map(_.io.finished)).reduce(_&&_)

  tests.foreach { _.io.start := (state === s_start) }
  io.finished := (state === s_done)

  when (state === s_idle) { state := s_start }
  when (state === s_start) { state := s_busy }
  when (state === s_busy && tests_finished) { state := s_done }
}
