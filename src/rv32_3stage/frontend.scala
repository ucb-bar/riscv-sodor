//**************************************************************************
// RISCV Processor Front-end
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2013 Jn 29
//
// Handling the fetching of instructions. 
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


package Sodor
{

import Chisel._
import Node._

import Constants._
import Common._


class FrontEndIO(implicit conf: SodorConfiguration) extends Bundle
{
   val cpu  = new FrontEndCpuIO
   val imem = new MemPortIo(conf.xprlen)
  
   override def clone = { new FrontEndIO().asInstanceOf[this.type] }
}

 
class FrontEndReq(xprlen: Int) extends Bundle
{
   val pc   = UInt(width = xprlen) 
   
   override def clone = { new FrontEndReq(xprlen).asInstanceOf[this.type] }
}
 

class FrontEndResp(xprlen: Int) extends Bundle
{
   val pc   = UInt(width = xprlen) 
   val inst = Bits(width = 32)  // only support 32b insts for now
   
   override def clone = { new FrontEndResp(xprlen).asInstanceOf[this.type] }
}
   

class FrontEndCpuIO(implicit conf: SodorConfiguration) extends Bundle
{
   val req = new DecoupledIO(new FrontEndReq(conf.xprlen)).flip
   val resp = new DecoupledIO(new FrontEndResp(conf.xprlen))
 
   val debug = new Bundle
   {
      val if_pc   = Bits(width = conf.xprlen)
      val if_inst = Bits(width = 32)
   }.asOutput
 
   override def clone = { new FrontEndCpuIO().asInstanceOf[this.type] }
}

class FrontEnd(implicit conf: SodorConfiguration) extends Module
{
   val io = new FrontEndIO


   //**********************************
   // Pipeline State Registers
//   val if_reg_valid  = Reg(init=Bool(false))
   val if_reg_pc     = Reg(init=UInt(START_ADDR, conf.xprlen))
    
//   val exe_reg_valid = Reg(init=Bool(false))
   val exe_reg_pc    = Reg(outType=UInt(width=conf.xprlen))
   val exe_reg_inst  = Reg(init=BUBBLE)

   //**********************************
   // Next PC Stage (if we can call it that)
   val if_pc_next = UInt()
//   if_reg_valid := Bool(false)

//      printf("fe, req.ready: %d\n", (io.cpu.req.ready))
   
   // stall IF/EXE if backend not ready
   when (io.cpu.resp.ready)
   {
      when (io.imem.req.ready)
      {
         if_reg_pc    := if_pc_next
//         if_reg_valid := Bool(true)
      }
      when (io.cpu.req.valid)
      {
         if_reg_pc    := io.cpu.req.bits.pc
//         if_reg_valid := Bool(true)
      }
//      when (!reset.toBool && Reg(next=reset.toBool))
//      {
//         if_reg_pc    := UInt(START_ADDR, conf.xprlen)
////         if_reg_valid := Bool(true)
//      }
   }
   .otherwise
   {
      if_reg_pc := if_reg_pc
   }

   val if_pc_plus4 = (if_reg_pc + UInt(4, conf.xprlen))               
   if_pc_next := if_pc_plus4
   

   // set up outputs to the instruction memory
//   io.imem.req.bits.addr := if_pc_next
   io.imem.req.bits.addr := if_reg_pc
   io.imem.req.valid    := Bool(true) // XXX: when is this not true? 
   io.imem.req.bits.fcn := M_XRD
   io.imem.req.bits.typ := MT_WU

   io.cpu.req.ready := Bool(true) // XXX: when is this not true?

   //**********************************
   // Inst Fetch/Return Stage

   // set the defaults
//   exe_reg_valid := Bool(false)
   exe_reg_inst  := BUBBLE
   exe_reg_pc    := if_reg_pc

   when (io.cpu.resp.ready)
   {
      when (io.imem.resp.valid)
//   when (io.imem.resp.valid && if_reg_valid)
      {
   //      exe_reg_valid := Bool(true)
         exe_reg_inst  := io.imem.resp.bits.data
      }
      when (io.cpu.req.valid)
      {
         // datapath is redirecting the PC stream (misspeculation)
   //      exe_reg_valid := Bool(false)
         exe_reg_inst  := BUBBLE
      }
   }
   .otherwise
   {
      exe_reg_inst  := exe_reg_inst
      exe_reg_pc    := exe_reg_pc
   }
   
   //**********************************
   // Execute Stage
   // (pass the instruction to the backend)

//   io.cpu.resp.valid     := exe_reg_valid
   io.cpu.resp.valid     := Bool(true)
   io.cpu.resp.bits.inst := exe_reg_inst
   io.cpu.resp.bits.pc   := exe_reg_pc
    
   //**********************************
   io.cpu.debug.if_pc := if_reg_pc
   io.cpu.debug.if_inst := io.imem.resp.bits.data
}

 
}
