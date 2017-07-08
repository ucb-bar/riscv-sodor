package zynq

import config._
import chisel3._
import chisel3.util._
import uncore.tilelink2._
import diplomacy._
import zynq._
import util._
import uncore.axi4._
import uncore.converters._
import uncore.util._
import Common._
import Common.Util._


class MemAccessToTL(num_core_ports: Int, num_bytes: Int = (1 << 21))(implicit conf: SodorConfiguration,p: Parameters) extends LazyModule {
   val masterInstr = TLClientNode(TLClientParameters(name = s"Core Instr"))
   val masterData = TLClientNode(TLClientParameters(name = s"Core Data"))
   lazy val module = new MemAccessToTLModule(this,num_core_ports)
}

class MemAccessToTLBundle(outer: MemAccessToTL,num_core_ports: Int)(implicit conf: SodorConfiguration,p: Parameters) extends Bundle(){
   val core_ports = Vec(num_core_ports, Flipped(new MemPortIo(data_width = conf.xprlen)) )
   val debug_port = Flipped(new MemPortIo(data_width = conf.xprlen))  
   val tl_data = outer.masterData.bundleOut
   val tl_instr = outer.masterInstr.bundleOut 
}

class MemAccessToTLModule(outer: MemAccessToTL,num_core_ports: Int, num_bytes: Int = (1 << 21))(implicit conf: SodorConfiguration,p: Parameters) extends LazyModuleImp(outer) with Common.MemoryOpConstants
{
   val io = new MemAccessToTLBundle(outer,num_core_ports)
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
   io.debug_port.req.ready := true.B // for now, no back pressure
   io.debug_port.resp.valid := Reg(next = io.debug_port.req.valid && io.debug_port.req.bits.fcn === M_XRD)
   // asynchronous read
   sync_data.io.hr.addr := io.debug_port.req.bits.addr
   io.debug_port.resp.bits.data := sync_data.io.hr.data
   sync_data.io.hw.en := Mux((io.debug_port.req.bits.fcn === M_XWR),true.B,false.B)
   when (io.debug_port.req.valid && io.debug_port.req.bits.fcn === M_XWR)
   {
      sync_data.io.hw.addr := io.debug_port.req.bits.addr
      sync_data.io.hw.data := io.debug_port.req.bits.data 
      sync_data.io.hw.mask := 15.U
   } 

}