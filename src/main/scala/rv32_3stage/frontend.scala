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


class FrontEndIO(implicit val conf: SodorCoreParams) extends Bundle
{
   val cpu  = new FrontEndCpuIO
   val imem = new MemPortIo(conf.xprlen)

   val reset_vector = Input(UInt())

}


class FrontEndReq(xprlen: Int) extends Bundle
{
   val pc   = UInt(xprlen.W)

}


class FrontEndResp(xprlen: Int) extends Bundle
{
   val pc   = UInt(xprlen.W)
   val inst = UInt(xprlen.W)  // only support 32b insts for now

}

class FrontEndDebug(xprlen: Int) extends Bundle
{
   val if_pc   = Output(UInt(xprlen.W))
   val if_inst = Output(UInt(xprlen.W))
}

class FrontEndCpuIO(implicit val conf: SodorCoreParams) extends Bundle
{
   val req = Flipped(new ValidIO(new FrontEndReq(conf.xprlen)))
   val resp = new DecoupledIO(new FrontEndResp(conf.xprlen))

   val debug = new FrontEndDebug(conf.xprlen)

   // Inst miss
   val imiss = Output(Bool())
   // Flush the entire pipeline upon exception, including exe stage
   val exe_kill = Input(Bool())

}


class FrontEnd(implicit val conf: SodorCoreParams) extends Module
{
   val io = IO(new FrontEndIO)
   io := DontCare

   //**********************************
   // Pipeline State Registers
   val if_reg_pc     = RegInit(io.reset_vector - 4.U)

   val exe_reg_valid = RegInit(false.B)
   val exe_reg_pc    = Reg(UInt(conf.xprlen.W))
   val exe_reg_inst  = Reg(UInt(conf.xprlen.W))

   //**********************************
   // Next PC Stage (if we can call it that)
   val if_pc_next = Wire(UInt(conf.xprlen.W))
   val if_val_next = Wire(Bool()) // for now, always true. But instruction
                                // buffers, etc., could make that not the case.

   val if_pc_plus4 = (if_reg_pc + 4.asUInt(conf.xprlen.W))

   // Redirect handling
   val if_redirected = RegInit(false.B)
   val if_redirected_pc = Reg(UInt(conf.xprlen.W))

   // Instruction buffer
   val if_buffer_in = Wire(new DecoupledIO(new MemResp(conf.xprlen)))
   if_buffer_in.valid := io.imem.resp.valid 
   if_buffer_in.bits := io.imem.resp.bits
   if_val_next := io.cpu.resp.ready || (if_buffer_in.ready && !io.imem.resp.valid) // If the incoming inst goes to buffer, don't send the next req
   assert(if_buffer_in.ready || !if_buffer_in.valid, "Inst buffer overflow")
   val if_buffer_out = Queue(if_buffer_in, entries = 1, pipe = false, flow = true)

   // stall IF/EXE if backend not ready
   if_pc_next := if_pc_plus4
   when (io.cpu.req.valid)
   {
      // datapath is redirecting the PC stream (misspeculation)
      if_redirected := true.B
      if_redirected_pc := io.cpu.req.bits.pc
   }
   when (if_redirected)
   {
      if_pc_next := if_redirected_pc
   }

   // Go to next PC if both CPU and imem are ready, and the memory response for the current PC already presents
   val if_reg_pc_responded = RegInit(false.B)
   val if_pc_responsed = if_reg_pc_responded || io.imem.resp.valid
   when (io.cpu.resp.ready && io.imem.req.ready && if_pc_responsed)
   {
      if_reg_pc_responded := false.B
      if_reg_pc    := if_pc_next
      when (!io.cpu.req.valid)
      {
         if_redirected := false.B
      }
   } .elsewhen (io.imem.resp.valid)
   {
      if_reg_pc_responded := true.B
   }

   // set up outputs to the instruction memory
   io.imem.req.bits.addr := if_pc_next
   io.imem.req.valid     := if_val_next && !io.cpu.req.valid
   io.imem.req.bits.fcn  := M_XRD
   io.imem.req.bits.typ  := MT_WU

   //**********************************
   // Inst Fetch/Return Stage
   if_buffer_out.ready := io.cpu.resp.ready
   when (io.cpu.exe_kill)
   {
      exe_reg_valid := false.B
   }
   .elsewhen (io.cpu.resp.ready)
   {
      exe_reg_valid := if_buffer_out.valid && !io.cpu.req.valid && !if_redirected
      exe_reg_pc    := if_reg_pc
      exe_reg_inst  := if_buffer_out.bits.data
   }

   //**********************************
   // Execute Stage
   // (pass the instruction to the backend)
   io.cpu.resp.valid     := exe_reg_valid
   io.cpu.resp.bits.inst := exe_reg_inst
   io.cpu.resp.bits.pc   := exe_reg_pc

   //**********************************
   // only used for debugging
   io.cpu.debug.if_pc := if_reg_pc
   io.cpu.debug.if_inst := io.imem.resp.bits.data
}
