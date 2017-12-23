// See LICENSE.SiFive for license details.

// If you know two clocks are related with a N:1 or 1:N relationship, you
// can cross the clock domains with lower latency than an AsyncQueue.
// This clock crossing behaves almost identically to a TLBuffer(2):
//   - It adds one cycle latency to each clock domain.
//   - All outputs of TLRational are registers (bits, valid, and ready).
//   - It costs 3*bits registers as opposed to 2*bits in a TLBuffer(2)

package uncore.tilelink2

import Chisel._
import chisel3.internal.sourceinfo.SourceInfo
import config._
import diplomacy._
import util._

class TLRationalCrossingSource(implicit p: Parameters) extends LazyModule
{
  val node = TLRationalSourceNode()

  lazy val module = new LazyModuleImp(this) {
    val io = new Bundle {
      val in  = node.bundleIn
      val out = node.bundleOut
    }

    ((io.in zip io.out) zip (node.edgesIn zip node.edgesOut)) foreach { case ((in, out), (edgeIn, edgeOut)) =>
      val bce = edgeIn.manager.anySupportAcquireB && edgeIn.client.anySupportProbe
      val direction = edgeOut.manager.direction

      out.a <> ToRational(in.a, direction)
      in.d <> FromRational(out.d, direction.flip)

      if (bce) {
        in.b <> FromRational(out.b, direction.flip)
        out.c <> ToRational(in.c, direction)
        out.e <> ToRational(in.e, direction)
      } else {
        in.b.valid   := Bool(false)
        in.c.ready   := Bool(true)
        in.e.ready   := Bool(true)
        out.b.ready  := Bool(true)
        out.c.valid  := Bool(false)
        out.e.valid  := Bool(false)
        out.b.sink   := UInt(0)
        out.c.source := UInt(0)
        out.e.source := UInt(0)
      }
    }
  }
}

class TLRationalCrossingSink(direction: RationalDirection = Symmetric)(implicit p: Parameters) extends LazyModule
{
  val node = TLRationalSinkNode(direction)

  lazy val module = new LazyModuleImp(this) {
    val io = new Bundle {
      val in  = node.bundleIn
      val out = node.bundleOut
    }

    ((io.in zip io.out) zip (node.edgesIn zip node.edgesOut)) foreach { case ((in, out), (edgeIn, edgeOut)) =>
      val bce = edgeOut.manager.anySupportAcquireB && edgeOut.client.anySupportProbe
      val direction = edgeIn.manager.direction

      out.a <> FromRational(in.a, direction)
      in.d <> ToRational(out.d, direction.flip)

      if (bce) {
        in.b <> ToRational(out.b, direction.flip)
        out.c <> FromRational(in.c, direction)
        out.e <> FromRational(in.e, direction)
      } else {
        out.b.ready := Bool(true)
        out.c.valid := Bool(false)
        out.e.valid := Bool(false)
        in.b.valid  := Bool(false)
        in.c.ready  := Bool(true)
        in.e.ready  := Bool(true)
        in.b.source := UInt(0)
        in.c.sink   := UInt(0)
        in.e.sink   := UInt(0)
      }
    }
  }
}

object TLRationalCrossingSource
{
  // applied to the TL source node; y.node := TLRationalCrossingSource()(x.node)
  def apply()(x: TLOutwardNode)(implicit p: Parameters, sourceInfo: SourceInfo): TLRationalOutwardNode = {
    val source = LazyModule(new TLRationalCrossingSource)
    source.node := x
    source.node
  }
}

object TLRationalCrossingSink
{
  // applied to the TL source node; y.node := TLRationalCrossingSink()(x.node)
  def apply(direction: RationalDirection = Symmetric)(x: TLRationalOutwardNode)(implicit p: Parameters, sourceInfo: SourceInfo): TLOutwardNode = {
    val sink = LazyModule(new TLRationalCrossingSink(direction))
    sink.node := x
    sink.node
  }
}

class TLRationalCrossing(direction: RationalDirection = Symmetric)(implicit p: Parameters) extends LazyModule
{
  val nodeIn = TLInputNode()
  val nodeOut = TLOutputNode()
  val node = NodeHandle(nodeIn, nodeOut)

  val source = LazyModule(new TLRationalCrossingSource)
  val sink = LazyModule(new TLRationalCrossingSink(direction))

  val _    = (sink.node := source.node) // no monitor
  val in   = (source.node := nodeIn)
  val out  = (nodeOut := sink.node)

  lazy val module = new LazyModuleImp(this) {
    val io = new Bundle {
      val in        = nodeIn.bundleIn
      val in_clock  = Clock(INPUT)
      val in_reset  = Bool(INPUT)
      val out       = nodeOut.bundleOut
      val out_clock = Clock(INPUT)
      val out_reset = Bool(INPUT)
    }

    source.module.clock := io.in_clock
    source.module.reset := io.in_reset
    in.foreach { lm =>
      lm.module.clock := io.in_clock
      lm.module.reset := io.in_reset
    }

    sink.module.clock := io.out_clock
    sink.module.reset := io.out_reset
    out.foreach { lm =>
      lm.module.clock := io.out_clock
      lm.module.reset := io.out_reset
    }
  }
}

/** Synthesizeable unit tests */
import unittest._

class TLRAMRationalCrossingSource(name: String, txns: Int)(implicit p: Parameters) extends LazyModule {
  val node = TLRationalOutputNode()
  val fuzz  = LazyModule(new TLFuzzer(txns))
  val model = LazyModule(new TLRAMModel(name))

  model.node := fuzz.node
  node := TLRationalCrossingSource()(TLDelayer(0.25)(model.node))

  lazy val module = new LazyModuleImp(this) {
    val io = new Bundle {
      val finished = Bool(OUTPUT)
      val out = node.bundleOut
    }
    io.finished := fuzz.module.io.finished
  }
}

class TLRAMRationalCrossingSink(direction: RationalDirection)(implicit p: Parameters) extends LazyModule {
  val node = TLRationalInputNode()
  val ram  = LazyModule(new TLRAM(AddressSet(0x0, 0x3ff)))

  ram.node := TLFragmenter(4, 256)(TLDelayer(0.25)(TLRationalCrossingSink(direction)(node)))

  lazy val module = new LazyModuleImp(this) {
    val io = new Bundle {
      val in = node.bundleIn
    }
  }
}

class TLRAMRationalCrossing(txns: Int)(implicit p: Parameters) extends LazyModule {
  val sym_fast_source = LazyModule(new TLRAMRationalCrossingSource("RationalCrossing sym_fast", txns))
  val sym_slow_sink   = LazyModule(new TLRAMRationalCrossingSink(Symmetric))
  sym_slow_sink.node := sym_fast_source.node

  val sym_slow_source = LazyModule(new TLRAMRationalCrossingSource("RationalCrossing sym_slow", txns))
  val sym_fast_sink   = LazyModule(new TLRAMRationalCrossingSink(Symmetric))
  sym_fast_sink.node := sym_slow_source.node

  val fix_fast_source = LazyModule(new TLRAMRationalCrossingSource("RationalCrossing fast", txns))
  val fix_slow_sink   = LazyModule(new TLRAMRationalCrossingSink(FastToSlow))
  fix_slow_sink.node := fix_fast_source.node

  val fix_slow_source = LazyModule(new TLRAMRationalCrossingSource("RationalCrossing slow", txns))
  val fix_fast_sink   = LazyModule(new TLRAMRationalCrossingSink(SlowToFast))
  fix_fast_sink.node := fix_slow_source.node

  lazy val module = new LazyModuleImp(this) with HasUnitTestIO {
    io.finished :=
      sym_fast_source.module.io.finished &&
      sym_slow_source.module.io.finished &&
      fix_fast_source.module.io.finished &&
      fix_slow_source.module.io.finished

    // Generate faster clock (still divided so verilator approves)
    val fast = Module(new util.Pow2ClockDivider(1))
    sym_fast_source.module.clock := fast.io.clock_out
    sym_fast_sink  .module.clock := fast.io.clock_out
    fix_fast_source.module.clock := fast.io.clock_out
    fix_fast_sink  .module.clock := fast.io.clock_out

    // Generate slower clock
    val slow = Module(new util.Pow2ClockDivider(2))
    fix_slow_source.module.clock := slow.io.clock_out
    fix_slow_sink  .module.clock := slow.io.clock_out

    val odd = Module(new util.ClockDivider3)
    odd.io.clk_in := clock
    sym_slow_source.module.clock := odd.io.clk_out
    sym_slow_sink  .module.clock := odd.io.clk_out
  }
}

class TLRAMRationalCrossingTest(txns: Int = 5000, timeout: Int = 500000)(implicit p: Parameters) extends UnitTest(timeout) {
  io.finished := Module(LazyModule(new TLRAMRationalCrossing(txns)).module).io.finished
}
