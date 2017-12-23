// See LICENSE.SiFive for license details.

package uncore.ahb

import Chisel._
import config._
import diplomacy._
import regmapper._
import scala.math.{min,max}

class AHBFanout()(implicit p: Parameters) extends LazyModule {
  val node = AHBNexusNode(
    numSlavePorts  = 1 to 1,
    numMasterPorts = 1 to 32,
    masterFn = { case Seq(m) => m },
    slaveFn  = { seq => seq(0).copy(slaves = seq.flatMap(_.slaves)) })

  lazy val module = new LazyModuleImp(this) {
    val io = new Bundle {
      val in = node.bundleIn
      val out = node.bundleOut
    }

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

    val in = io.in(0)
    val a_sel = Vec(route_addrs.map(seq => seq.map(_.contains(in.haddr)).reduce(_ || _)))
    val d_sel = Reg(a_sel)

    when (in.hready) { d_sel := a_sel }
    (a_sel zip io.out) foreach { case (sel, out) =>
      out := in
      out.hsel := in.hsel && sel
    }

    in.hreadyout := !Mux1H(d_sel, io.out.map(!_.hreadyout))
    in.hresp     :=  Mux1H(d_sel, io.out.map(_.hresp))
    in.hrdata    :=  Mux1H(d_sel, io.out.map(_.hrdata))
  }
}
