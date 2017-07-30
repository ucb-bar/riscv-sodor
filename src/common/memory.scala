//**************************************************************************
// Scratchpad Memory (asynchronous)
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2013 Jun 12
//
// Provides a variable number of ports to the core, and one port to the HTIF
// (host-target interface).
//
// Assumes that if the port is ready, it will be performed immediately.
// For now, don't detect write collisions.
//
// Optionally uses synchronous read (default is async). For example, a 1-stage
// processor can only ever work using asynchronous memory!

package Common
{

import chisel3._
import chisel3.util._
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import chisel3.experimental._

import Constants._
import Common._
import Common.Util._

trait MemoryOpConstants 
{
   val MT_X  = 0.asUInt(3.W)
   val MT_B  = 1.asUInt(3.W)
   val MT_H  = 2.asUInt(3.W)
   val MT_W  = 3.asUInt(3.W)
   val MT_D  = 4.asUInt(3.W)
   val MT_BU = 5.asUInt(3.W)
   val MT_HU = 6.asUInt(3.W)
   val MT_WU = 7.asUInt(3.W)

   val M_X   = "b0".asUInt(1.W)
   val M_XRD = "b0".asUInt(1.W) // int load
   val M_XWR = "b1".asUInt(1.W) // int store

   val DPORT = 0
   val IPORT = 1
}

class Rport(val addrWidth : Int,val dataWidth : Int) extends Bundle{
   val addr = Input(UInt(addrWidth.W))
   val data = Output(UInt(dataWidth.W))
   override def cloneType = { new Rport(addrWidth,dataWidth).asInstanceOf[this.type] }
}

class Wport(val addrWidth : Int,val dataWidth : Int) extends Bundle{
   val maskWidth = dataWidth/8
   val addr = Input(UInt(addrWidth.W))
   val data = Input(UInt(dataWidth.W))
   val mask = Input(UInt(maskWidth.W))
   val en = Input(Bool())
   override def cloneType = { new Wport(addrWidth,dataWidth).asInstanceOf[this.type] }
}

class d2h2i1(val addrWidth : Int) extends Bundle{
   val dataInstr = Vec(2,new  Rport(addrWidth,32))
   val hw = new  Wport(addrWidth,32)
   val dw = new  Wport(addrWidth,32)
   val hr = new  Rport(addrWidth,32)
   val clk = Input(Clock()) 
}

class AsyncReadMem(val addrWidth : Int) extends BlackBox{
   val io = IO(new d2h2i1(addrWidth))
}

class SyncMem(val addrWidth : Int) extends BlackBox{
   val io = IO(new d2h2i1(addrWidth))
}

// from the pov of the datapath
class MemPortIo(data_width: Int)(implicit conf: SodorConfiguration) extends Bundle 
{
   val req    = new DecoupledIO(new MemReq(data_width))
   val resp   = Flipped(new ValidIO(new MemResp(data_width)))
  override def cloneType = { new MemPortIo(data_width).asInstanceOf[this.type] }
}

class MemReq(data_width: Int)(implicit conf: SodorConfiguration) extends Bundle
{
   val addr = Output(UInt(conf.xprlen.W))
   val data = Output(UInt(data_width.W))
   val fcn  = Output(UInt(M_X.getWidth.W))  // memory function code
   val typ  = Output(UInt(MT_X.getWidth.W)) // memory type
  override def cloneType = { new MemReq(data_width).asInstanceOf[this.type] }
}

class MemResp(data_width: Int) extends Bundle
{
   val data = Output(UInt(data_width.W))
  override def cloneType = { new MemResp(data_width).asInstanceOf[this.type] }
}

// NOTE: the default is enormous (and may crash your computer), but is bound by
// what the fesvr expects the smallest memory size to be.  A proper fix would
// be to modify the fesvr to expect smaller sizes.
//for 1,2 and 5 stage need for combinational reads 
class AsyncScratchPadMemory(num_core_ports: Int, num_bytes: Int = (1 << 21))(implicit conf: SodorConfiguration) extends Module
{
   val io = IO(new Bundle
   {
      val core_ports = Vec(num_core_ports, Flipped(new MemPortIo(data_width = conf.xprlen)) )
      val debug_port = Flipped(new MemPortIo(data_width = 32))
   })
   val num_bytes_per_line = 8
   val num_lines = num_bytes / num_bytes_per_line
   println("\n    Sodor Tile: creating Asynchronous Scratchpad Memory of size " + num_lines*num_bytes_per_line/1024 + " kB\n")
   val async_data = Module(new AsyncReadMem(log2Ceil(num_bytes)))
   async_data.io.clk := clock
   for (i <- 0 until num_core_ports)
   {
      io.core_ports(i).resp.valid := io.core_ports(i).req.valid
      io.core_ports(i).req.ready := Bool(true) // for now, no back pressure 
      async_data.io.dataInstr(i).addr := io.core_ports(i).req.bits.addr
   }

   /////////// DPORT 
   val req_addri = io.core_ports(DPORT).req.bits.addr

   val req_typi = io.core_ports(DPORT).req.bits.typ
   val resp_datai = async_data.io.dataInstr(DPORT).data
   io.core_ports(DPORT).resp.bits.data := MuxCase(resp_datai,Array(
      (req_typi === MT_B) -> Cat(Fill(24,resp_datai(7)),resp_datai(7,0)),
      (req_typi === MT_H) -> Cat(Fill(16,resp_datai(15)),resp_datai(15,0)),
      (req_typi === MT_BU) -> Cat(Fill(24,0.U),resp_datai(7,0)),
      (req_typi === MT_HU) -> Cat(Fill(16,0.U),resp_datai(15,0))
   ))
   async_data.io.dw.en := Mux((io.core_ports(DPORT).req.bits.fcn === M_XWR),Bool(true),Bool(false))
   when (io.core_ports(DPORT).req.valid && (io.core_ports(DPORT).req.bits.fcn === M_XWR))
   {
      async_data.io.dw.data := io.core_ports(DPORT).req.bits.data << (req_addri(1,0) << 3)
      async_data.io.dw.addr := Cat(req_addri(31,2),0.asUInt(2.W))
      async_data.io.dw.mask := Mux(req_typi === MT_B,1.U << req_addri(1,0),
                              Mux(req_typi === MT_H,3.U << req_addri(1,0),15.U))
   }
   /////////////////

   ///////////// IPORT
   if (num_core_ports == 2){
      io.core_ports(IPORT).resp.bits.data := async_data.io.dataInstr(IPORT).data
   }
   ////////////

   // DEBUG PORT-------
   io.debug_port.req.ready := Bool(true) // for now, no back pressure
   io.debug_port.resp.valid := io.debug_port.req.valid
   // asynchronous read
   async_data.io.hr.addr := io.debug_port.req.bits.addr
   io.debug_port.resp.bits.data := async_data.io.hr.data
   async_data.io.hw.en := Mux((io.debug_port.req.bits.fcn === M_XWR) && io.debug_port.req.valid,true.B,false.B)
   when (io.debug_port.req.valid && io.debug_port.req.bits.fcn === M_XWR)
   {
      async_data.io.hw.addr := io.debug_port.req.bits.addr
      async_data.io.hw.data := io.debug_port.req.bits.data 
      async_data.io.hw.mask := 15.U
   } 
}

class SyncScratchPadMemory(num_core_ports: Int, num_bytes: Int = (1 << 21))(implicit conf: SodorConfiguration) extends Module
{
   val io = IO(new Bundle
   {
      val core_ports = Vec(num_core_ports, Flipped(new MemPortIo(data_width = conf.xprlen)) )
      val debug_port = Flipped(new MemPortIo(data_width = 32))
   })
   val num_bytes_per_line = 8
   val num_lines = num_bytes / num_bytes_per_line
   println("\n    Sodor Tile: creating Synchronous Scratchpad Memory of size " + num_lines*num_bytes_per_line/1024 + " kB\n")
   val sync_data = Module(new SyncMem(log2Ceil(num_bytes)))
   sync_data.io.clk := clock
   for (i <- 0 until num_core_ports)
   {
      io.core_ports(i).resp.valid := Reg(next = io.core_ports(i).req.valid)
      io.core_ports(i).req.ready := true.B // for now, no back pressure 
      sync_data.io.dataInstr(i).addr := io.core_ports(i).req.bits.addr
   }

   /////////// DPORT 
   val req_addri = io.core_ports(DPORT).req.bits.addr

   val req_typi = Reg(UInt(3.W))
   req_typi := io.core_ports(DPORT).req.bits.typ
   val resp_datai = sync_data.io.dataInstr(DPORT).data

   io.core_ports(DPORT).resp.bits.data := MuxCase(resp_datai,Array(
      (req_typi === MT_B) -> Cat(Fill(24,resp_datai(7)),resp_datai(7,0)), 
      (req_typi === MT_H) -> Cat(Fill(16,resp_datai(15)),resp_datai(15,0)), 
      (req_typi === MT_BU) -> Cat(Fill(24,0.U),resp_datai(7,0)), 
      (req_typi === MT_HU) -> Cat(Fill(16,0.U),resp_datai(15,0)) 
   ))

   sync_data.io.dw.en := Mux((io.core_ports(DPORT).req.bits.fcn === M_XWR),true.B,false.B)
   when (io.core_ports(DPORT).req.valid && (io.core_ports(DPORT).req.bits.fcn === M_XWR))
   {
      sync_data.io.dw.data := io.core_ports(DPORT).req.bits.data << (req_addri(1,0) << 3)
      sync_data.io.dw.addr := Cat(req_addri(31,2),0.asUInt(2.W))
      sync_data.io.dw.mask := Mux(io.core_ports(DPORT).req.bits.typ === MT_B,1.U << req_addri(1,0),
                              Mux(io.core_ports(DPORT).req.bits.typ === MT_H,3.U << req_addri(1,0),15.U))
   }
   /////////////////

   ///////////// IPORT
   if (num_core_ports == 2)
      io.core_ports(IPORT).resp.bits.data := sync_data.io.dataInstr(IPORT).data 
   ////////////

   // DEBUG PORT-------
   io.debug_port.req.ready := Reg(next = io.debug_port.req.valid) // for now, no back pressure
   io.debug_port.resp.valid := Mux(io.debug_port.req.bits.fcn === M_XWR,io.debug_port.req.valid,
                              Reg(next = io.debug_port.req.valid))
   // asynchronous read
   sync_data.io.hr.addr := io.debug_port.req.bits.addr
   io.debug_port.resp.bits.data := sync_data.io.hr.data
   sync_data.io.hw.en := Mux((io.debug_port.req.bits.fcn === M_XWR) && io.debug_port.req.valid,true.B,false.B)
   when (io.debug_port.req.valid && io.debug_port.req.bits.fcn === M_XWR)
   {
      sync_data.io.hw.addr := io.debug_port.req.bits.addr
      sync_data.io.hw.data := io.debug_port.req.bits.data 
      sync_data.io.hw.mask := 15.U
   }
   printf("MEM %x %x %x %x",io.debug_port.req.valid,io.debug_port.req.bits.data,io.debug_port.req.bits.addr,io.debug_port.req.bits.fcn) 
}

}
