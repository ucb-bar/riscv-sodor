//**************************************************************************
// RISCV Processor Front-end
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2013 Jn 29
//
// Handles the fetching of instructions.
//
// The front-end will go into cruise-control and fetch whichever instrutions it
// feels like (probably just PC+4 for this simple pipeline), and it's the job
// of the datapath to assert "valid" on the "io.req" bundle when it wants to
// redirect the fetch PC.  Otherwise, io.req.valid is disasserted.

// There are a number of games we can play with the front-end.
//    - It can fetch doublewords, thus only using the memory port on every
//    other cycle.
//    - It can have a fetch buffer (combined with dw fetch) to hide memory port
//    hazards.
//     - It can use a stall cache (Krste Asanovic paper), which holds a cache
//     of previously stalled instructions.
//
// None of the above are implemented - I leave them as an excercise to the
// reader for now...


package sodor.stage3

import chisel3._
import chisel3.util._


import Constants._
import sodor.common._

import freechips.rocketchip.tile.TileInputConstants

class FrontEndIO(implicit val conf: SodorConfiguration) extends Bundle
{
   val cpu  = new FrontEndCpuIO
   val imem = new MemPortIo(conf.xprlen)

   val constants = new TileInputConstants()(conf.p)

   override def cloneType = { new FrontEndIO().asInstanceOf[this.type] }
}


class FrontEndReq(xprlen: Int) extends Bundle
{
   val pc   = UInt(xprlen.W)

   override def cloneType = { new FrontEndReq(xprlen).asInstanceOf[this.type] }
}


class FrontEndResp(xprlen: Int) extends Bundle
{
   val pc   = UInt(xprlen.W)
   val inst = UInt(xprlen.W)  // only support 32b insts for now

   override def cloneType = { new FrontEndResp(xprlen).asInstanceOf[this.type] }
}

class FrontEndDebug(xprlen: Int) extends Bundle
{
   val if_pc   = Output(UInt(xprlen.W))
   val if_inst = Output(UInt(xprlen.W))
   override def cloneType = { new FrontEndDebug(xprlen).asInstanceOf[this.type] }
}

class FrontEndCpuIO(implicit val conf: SodorConfiguration) extends Bundle
{
   val req = Flipped(new ValidIO(new FrontEndReq(conf.xprlen)))
   val resp = new DecoupledIO(new FrontEndResp(conf.xprlen))

   val debug = new FrontEndDebug(conf.xprlen)

   // Instruction-fetch PC change flag - raise for one cycle every time the PC is changed
   val if_pc_change = Output(Bool())

   override def cloneType = { new FrontEndCpuIO().asInstanceOf[this.type] }
}

class FrontEnd(implicit val conf: SodorConfiguration) extends Module
{
   val io = IO(new FrontEndIO)
   io := DontCare

   // PC change flag (see the IO port above)
   val reg_if_pc_change = RegInit(false.B)
   io.cpu.if_pc_change := reg_if_pc_change

   //**********************************
   // Pipeline State Registers
   val if_reg_valid  = RegInit(false.B)
   val if_reg_kill   = RegInit(false.B)
   val if_reg_pc     = RegInit(io.constants.reset_vector)
   val if_reg_inst   = Reg(UInt(conf.xprlen.W))

   val exe_reg_valid = RegInit(false.B)
   val exe_reg_pc    = Reg(UInt(conf.xprlen.W))
   val exe_reg_inst  = Reg(UInt(conf.xprlen.W))

   //**********************************
   // Next PC Stage (if we can call it that)
   val if_pc_plus4 = (if_reg_pc + 4.asUInt(conf.xprlen.W))
   val if_pc_next = Mux(io.cpu.req.valid, io.cpu.req.bits.pc, if_pc_plus4)

   // io.cpu.req.valid signal a change to PC from PC+4, which requires us to kill the next instruction coming in.
   // Note that this should not affect the instruction already available in this cycle.
   when (io.cpu.req.valid) {
      when (if_reg_valid && !io.cpu.resp.ready) {
         // If there is an instruction in the buffer and it is not leaving the buffer this cycle, simply invalidate it.
         // This will take effect next cycle.
         if_reg_valid := false.B
      } .otherwise {
         if_reg_kill := true.B
      }
      
   }

   // A instruction is ready when it arrives or has been stored in buffer and hasn't been killed. Again, 
   // io.cpu.req.valid do not affect instruction that's already available.
   val if_valid = (if_reg_valid || io.imem.resp.valid) && !if_reg_kill
   // And when a new instruction arrived, the if_reg_kill must be cleared. That instruction will be invalidated.
   when (io.imem.resp.valid) { if_reg_kill := false.B }
   

   // If the current instruction is taken or the instruction stream changes, go to next instruction
   when ((io.cpu.resp.ready && if_valid) || io.cpu.req.valid)
   {
      if_reg_pc := if_pc_next
      // Produce a pulse when PC changes
      reg_if_pc_change := true.B
   } .elsewhen (reg_if_pc_change) {
      reg_if_pc_change := false.B
   }

   // If the instruction is not immediately taken by the core, put it into the buffer.
   when (!io.cpu.resp.ready && io.imem.resp.valid) {
      // If the fetched instruction is killed, simply throw away the data
      when (if_reg_kill) { if_reg_valid := false.B }
      .otherwise {
         // If not, and if the core is not ready, store it to the buffer.
         if_reg_valid := true.B
         if_reg_inst := io.imem.resp.bits.data
      }
   } .otherwise {
      // If resumed, clear the buffer.
      if_reg_inst := BUBBLE
      if_reg_valid := false.B
   }
   
   // Instruction repeated: when we start executing on SyncMem scratchpad (with fetch request sent in cycle 0),
   // the request will still be up in cycle 1 with PC remains the same, causing us to get the same instruction
   // at cycle 2. We will need to invalidate the instruction we get on cycle 2 in this case.
   // This will not affect the bus since the bus will not interpret the req.valid signal on the same cycle when the
   // previous request returns as a new request.
   //val inst_repeated = RegNext(!reg_if_pc_change && io.cpu.resp.fire())

   // set up outputs to the instruction memory
   io.imem.req.bits.addr := if_reg_pc
   io.imem.req.valid     := io.cpu.resp.ready  // New request should be sent only if the core can accept instruction.
   io.imem.req.bits.fcn  := M_XRD
   io.imem.req.bits.typ  := MT_WU

   //**********************************
   // Execute Stage
   // (pass the instruction to the backend)
   when (io.cpu.resp.ready && if_valid) {
      exe_reg_valid  := if_valid //&& !inst_repeated
      exe_reg_inst   := Mux(if_reg_kill, BUBBLE, Mux(if_reg_valid, if_reg_inst, io.imem.resp.bits.data))
      exe_reg_pc     := if_reg_pc
   }

   io.cpu.resp.valid     := exe_reg_valid
   io.cpu.resp.bits.inst := exe_reg_inst
   io.cpu.resp.bits.pc   := exe_reg_pc

   //**********************************
   // only used for debugging
   io.cpu.debug.if_pc := if_reg_pc
   io.cpu.debug.if_inst := io.imem.resp.bits.data
}
