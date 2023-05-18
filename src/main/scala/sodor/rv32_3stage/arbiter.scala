//**************************************************************************
// Arbiter for Princeton Architectures
//--------------------------------------------------------------------------
//
// Arbitrates instruction and data accesses to a single port memory.

package sodor.stage3

import chisel3._
import chisel3.util._

import sodor.common._

// arbitrates memory access
class SodorMemArbiter(implicit val conf: SodorCoreParams) extends Module
{
  val io = IO(new Bundle
    {
      // TODO I need to come up with better names... this is too confusing
      // from the point of view of the other modules
      val imem = Flipped(new MemPortIo(conf.xprlen)) // instruction fetch
      val dmem = Flipped(new MemPortIo(conf.xprlen)) // load/store
      val mem  = new MemPortIo(conf.xprlen)      // the single-ported memory
    })

  val d_resp = RegInit(false.B)

  // hook up requests
  // d_resp ensures that data req gets access to bus only
  // for one cycle
  // alternate between data and instr to avoid starvation
  when (d_resp)
  {
    // Last request is a data request - do not allow data request this cycle
    io.dmem.req.ready := false.B
    io.imem.req.ready := io.mem.req.ready

    // We only clear the d_resp flag when the next request fired since it also indicates the allowed type of the next request
    when (io.mem.req.fire)
    {
      d_resp := false.B
    }
  }
  .otherwise
  {
    // Last request is not a data request - if this cycle has a new data request, dispatch it
    io.dmem.req.ready := io.mem.req.ready
    io.imem.req.ready := io.mem.req.ready && !io.dmem.req.valid

    when (io.dmem.req.fire)
    {
      d_resp := true.B
    }
  }
  // SWITCH BET DATA AND INST REQ FOR SINGLE PORT
  when (io.dmem.req.fire)
  {
    io.mem.req.valid     := io.dmem.req.valid
    io.mem.req.bits.addr := io.dmem.req.bits.addr
    io.mem.req.bits.fcn  := io.dmem.req.bits.fcn
    io.mem.req.bits.typ  := io.dmem.req.bits.typ
  }
  .otherwise
  {
    io.mem.req.valid     := io.imem.req.valid
    io.mem.req.bits.addr := io.imem.req.bits.addr
    io.mem.req.bits.fcn  := io.imem.req.bits.fcn
    io.mem.req.bits.typ  := io.imem.req.bits.typ
  }
  // Control valid signal
  when (d_resp)
  {
    io.imem.resp.valid := false.B
    io.dmem.resp.valid := io.mem.resp.valid
  }
  .otherwise {
    io.imem.resp.valid := io.mem.resp.valid
    io.dmem.resp.valid := false.B
  }

  // No need to switch data since instruction port doesn't write
  io.mem.req.bits.data := io.dmem.req.bits.data

  // Simply connect response data to both ports since we only have one inflight request
  // the validity of the data is controlled above
  io.imem.resp.bits.data := io.mem.resp.bits.data
  io.dmem.resp.bits.data := io.mem.resp.bits.data
}
