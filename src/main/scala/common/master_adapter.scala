package sodor.common

import chisel3._
import chisel3.util._

import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.{LogicalTreeNode}
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem.{RocketCrossingParams}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.amba.axi4._

class SodorMasterAdapter(implicit p: Parameters, val conf: SodorConfiguration) extends LazyModule {
  // The node exposed to the crossbar
  val node = TLIdentityNode()

  // The client node
  val idBits = 4
  val masterNode = TLClientNode(Seq(TLMasterPortParameters.v1(
    clients = Seq(TLClientParameters(
      name = "sodor-mmio-master",
      sourceId = IdRange(0, 1 << idBits)
    ))
  )))

  // Connect nodes
  (node := TLBuffer()
    := TLFIFOFixer(TLFIFOFixer.all) // fix FIFO ordering
    := TLWidthWidget(1) // reduce size of TL to 1
    := masterNode)

  lazy val module = new SodorMasterAdapterImp(this)
}

class SodorMasterAdapterImp(outer: SodorMasterAdapter) extends LazyModuleImp(outer) {
  implicit val conf = outer.conf

  val io = IO(new Bundle() {
    val dport = Flipped(new MemPortIo(data_width = conf.xprlen))
  })

  val (tl_out, edge) = outer.masterNode.out(0)

  // The number of inflight request (Pending)
  val a_source = 0.U
  // val inflight_limit = (1 << outer.idBits - 1).U(outer.idBits.W)
  // val inflight_request = RegInit(0.U(outer.idBits.W))
  // val increment_inflight = inflight_request =/= inflight_limit & tl_out.a.valid & tl_out.a.ready
  // val decrement_inflight = tl_out.d.valid
  // inflight_request := Mux(increment_inflight ^ decrement_inflight, 
  //   Mux(decrement_inflight, inflight_request - 1.U, inflight_request + 1.U),
  // inflight_request)
  // val inflight_full = inflight_request === inflight_limit

  // Connect Channel A valid/ready
  tl_out.a.valid := io.dport.req.valid
  io.dport.req.ready := tl_out.a.ready
  // Connect Channel D valid/ready
  tl_out.d.ready := true.B
  io.dport.resp.valid := tl_out.d.valid

  // Build "Get" message
  // TODO: check typ signal here
  val (legal_get, get_bundle) = edge.Get(a_source, io.dport.req.bits.addr, io.dport.req.bits.typ)
  // Build "Put" message
  val (legal_put, put_bundle) = edge.Put(a_source, io.dport.req.bits.addr, io.dport.req.bits.typ, io.dport.req.bits.data)

  // Connect Channel A bundle
  tl_out.a.bits := Mux(io.dport.req.bits.fcn === M_XRD, get_bundle, put_bundle)

  // Connect Channel D bundle (read result)
  io.dport.resp.bits.data := tl_out.d.bits.data

  // TODO: handle error and deal with bit extension

  // Tie off unused channels
  tl_out.b.valid := false.B
  tl_out.c.ready := true.B
  tl_out.e.ready := true.B
}