// See LICENSE.SiFive for license details.

package uncore.apb

import Chisel._
import config._
import diplomacy._
import regmapper._
import scala.math.{min,max}

class APBFanout()(implicit p: Parameters) extends LazyModule {
  val node = APBNexusNode(
    numSlavePorts  = 1 to 1,
    numMasterPorts = 1 to 32,
    masterFn = { case Seq(m) => m },
    slaveFn  = { seq => seq(0).copy(slaves = seq.flatMap(_.slaves)) })

  lazy val module = new LazyModuleImp(this) {
    val io = new Bundle {
      val in = node.bundleIn
      val out = node.bundleOut
    }

    val in = io.in(0)

    // Require consistent bus widths
    val port0 = node.edgesOut(0).slave
    node.edgesOut.foreach { edge =>
      val port = edge.slave
      require (port.beatBytes == port0.beatBytes,
        s"${port.slaves.map(_.name)} ${port.beatBytes} vs ${port0.slaves.map(_.name)} ${port0.beatBytes}")
    }

    val port_addrs = node.edgesOut.map(_.slave.slaves.map(_.address).flatten)
    val routingMask = AddressDecoder(port_addrs)
    val route_addrs = port_addrs.map(_.map(_.widen(~routingMask)).distinct)

    val sel = Vec(route_addrs.map(seq => seq.map(_.contains(in.paddr)).reduce(_ || _)))
    (sel zip io.out) foreach { case (sel, out) =>
      out := in
      out.psel    := sel && in.psel
      out.penable := sel && in.penable
    }

    in.pready  := !Mux1H(sel, io.out.map(!_.pready))
    in.pslverr :=  Mux1H(sel, io.out.map(_.pslverr))
    in.prdata  :=  Mux1H(sel, io.out.map(_.prdata))
  }
}
