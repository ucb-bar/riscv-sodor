//**************************************************************************
// Arbiter for Princeton Architectures
//--------------------------------------------------------------------------
//
// Arbitrates instruction and data accesses to a single port memory.

package Sodor
{

import Chisel._
import Node._
import Common._


// arbitrates memory access
class SodorMemArbiter(implicit val conf: SodorConfiguration) extends Module
{
   val io = new Bundle
   {
      // TODO I need to come up with better names... this is too confusing 
      // from the point of view of the other modules
      val imem = new MemPortIo(conf.xprlen).flip // instruction fetch
      val dmem = new MemPortIo(conf.xprlen).flip // load/store 
      val mem  = new MemPortIo(conf.xprlen)      // the single-ported memory
   }

   //***************************
   // Figure out who gets to go
   val req_fire_dmem = Bool()
   val req_fire_imem = Bool()

   // default
   req_fire_dmem := Bool(false) 
   req_fire_imem := Bool(false) 

   when (io.dmem.req.valid)
   {
      req_fire_dmem := Bool(true)
   }
   .otherwise
   {
      req_fire_imem := Bool(true)
   }
 

   //***************************
   // apply back pressure as needed
   // let dmem always go through, hold up instruction fetch as necessary
   io.imem.req.ready := !req_fire_dmem
   io.dmem.req.ready := Bool(true)
                

   //***************************
   // hook up requests

   io.mem.req.valid     := io.imem.req.valid
   io.mem.req.bits.addr := io.imem.req.bits.addr
   io.mem.req.bits.fcn  := io.imem.req.bits.fcn
   io.mem.req.bits.typ  := io.imem.req.bits.typ

   when (req_fire_dmem)
   {
      io.mem.req.valid     := io.dmem.req.valid
      io.mem.req.bits.addr := io.dmem.req.bits.addr
      io.mem.req.bits.fcn  := io.dmem.req.bits.fcn
      io.mem.req.bits.typ  := io.dmem.req.bits.typ
   }
   io.mem.req.bits.data := io.dmem.req.bits.data


   //***************************
   // hook up responses

   io.imem.resp.valid := req_fire_imem
   io.dmem.resp.valid := req_fire_dmem
   io.imem.resp.bits.data := io.mem.resp.bits.data
   io.dmem.resp.bits.data := io.mem.resp.bits.data

}
 
}
