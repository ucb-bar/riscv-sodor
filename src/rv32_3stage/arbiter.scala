//**************************************************************************
// Arbiter for Princeton Architectures
//--------------------------------------------------------------------------
//
// Arbitrates instruction and data accesses to a single port memory.

package Sodor
{

import chisel3._
import chisel3.util._

import Common._

// arbitrates memory access
class SodorMemArbiter(implicit val conf: SodorConfiguration) extends Module
{
   val io = IO(new Bundle
      {
         // TODO I need to come up with better names... this is too confusing
         // from the point of view of the other modules
         val imem = Flipped(new MemPortIo(conf.xprlen)) // instruction fetch
         val dmem = Flipped(new MemPortIo(conf.xprlen)) // load/store
         val mem  = new MemPortIo(conf.xprlen)      // the single-ported memory
      })

   //***************************
   val i1reg = Reg(UInt(conf.xprlen.W))
   val d1reg = Reg(UInt(conf.xprlen.W))
   val nextdreq = RegInit(true.B)
   io.dmem.req.ready := true.B
   //d_fire : when true data request will be put on bus
   val d_fire = Wire(Bool())
   io.imem.req.ready := d_fire
   //***************************
   // hook up requests
   // 3 cycle FSM on LW , SW , FENCE in exe stage
   // HAZ since contention for MEM PORT so next cycle STALL
   // CYC 1 : Store inst in reg requested in prev CYC
   //         make data addr available on MEM PORT
   // CYC 2 : Store data in reg to be used in next CYC
   // CYC 3 : Default State with data addr on MEM PORT
   // nextdreq ensures that data req gets access to bus only
   // for one cycle
   // alternate between data and instr to avoid starvation
   when (io.dmem.req.valid && nextdreq)
   {
        d_fire := true.B
        nextdreq := false.B // allow only instr in next cycle
        io.imem.resp.valid := io.mem.resp.valid
   }
   .elsewhen(io.dmem.req.valid && !nextdreq)
   {
        d_fire := false.B
        nextdreq := true.B  // allow any future data request
        io.imem.resp.valid := false.B
   }
   .otherwise
   {
        d_fire := false.B
        io.imem.resp.valid := io.mem.resp.valid
   }
   // SwITCH BET DATA AND INST REQ FOR SINGLE PORT
   when (d_fire)
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

   io.mem.req.bits.data := io.dmem.req.bits.data
   io.dmem.resp.valid := io.mem.resp.valid && !io.imem.resp.valid

   when (!nextdreq){
      d1reg := io.mem.resp.bits.data
   }

   when (io.imem.resp.valid && io.dmem.req.valid && nextdreq){
      i1reg := io.mem.resp.bits.data
   }

   io.imem.resp.bits.data := Mux( !io.imem.resp.valid && io.dmem.req.valid && !nextdreq , i1reg , io.mem.resp.bits.data )
   io.dmem.resp.bits.data := d1reg
}

}
