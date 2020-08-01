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

  // The client node (only one inflight request supported for Sodor)
  val masterNode = TLClientNode(Seq(TLMasterPortParameters.v1(
    clients = Seq(TLClientParameters(
      name = "sodor-mmio-master",
      sourceId = IdRange(0, 1)
    ))
  )))

  // Connect nodes
  (node := TLBuffer() := masterNode)

  lazy val module = new SodorMasterAdapterImp(this)
}

class SodorMasterAdapterImp(outer: SodorMasterAdapter) extends LazyModuleImp(outer) {
  implicit val conf = outer.conf

  val io = IO(new Bundle() {
    val dport = Flipped(new MemPortIo(data_width = conf.xprlen))
  })

  val (tl_out, edge) = outer.masterNode.out(0)

  // Register
  // Inflight request exists
  val inflight = RegInit(false.B)
  // Address and signedness of the request to be used by LoadGen
  val a_address_reg = RegInit(0.U(io.dport.req.bits.addr.getWidth.W))
  val a_signed_reg = RegInit(false.B)

  // Sign logic
  // To convert MemPortIO type to sign and size in TileLink format: subtract 1 from type, then take MSB as signedness
  // and the remaining two bits as TileLink size
  val a_signed = (io.dport.req.bits.typ - 1.U)(2)
  val a_size = (io.dport.req.bits.typ - 1.U)(1, 0)

  // Connect Channel A valid/ready
  // If there is an inflight request, do not allow new request to be sent
  tl_out.a.valid := io.dport.req.valid & !inflight
  io.dport.req.ready := tl_out.a.ready & !inflight
  // Connect Channel D valid/ready
  tl_out.d.ready := true.B
  io.dport.resp.valid := tl_out.d.valid
  // States bookkeeping
  when (tl_out.a.fire()) {
    inflight := true.B
    a_address_reg := io.dport.req.bits.addr
    a_signed_reg := a_size
  }
  when (tl_out.d.fire()) {
    inflight := false.B
  }

  // Build "Get" message
  val (legal_get, get_bundle) = edge.Get(0.U, io.dport.req.bits.addr, a_size)
  // Build "Put" message
  val (legal_put, put_bundle) = edge.Put(0.U, io.dport.req.bits.addr, a_size, io.dport.req.bits.data)

  // Connect Channel A bundle
  tl_out.a.bits := Mux(io.dport.req.bits.fcn === M_XRD, get_bundle, put_bundle)

  // Connect Channel D bundle (read result)
  io.dport.resp.bits.data := new LoadGen(tl_out.d.bits.size, a_signed_reg, a_address_reg, tl_out.d.bits.data, false.B, conf.xprlen).data

  // Handle error
  val legal_op = Mux(io.dport.req.bits.fcn === M_XRD, legal_get, legal_put)
  val resp_xp = tl_out.d.bits.corrupt | tl_out.d.bits.denied
  // Since the core doesn't have an external exception port, we have to kill it
  assert(legal_op, "Illegal operation")
  assert(resp_xp, "Responds exception")

  // Tie off unused channels
  tl_out.b.valid := false.B
  tl_out.c.ready := true.B
  tl_out.e.ready := true.B
}