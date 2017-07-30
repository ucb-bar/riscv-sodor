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
   val masterDebug = TLClientNode(TLClientParameters(name = s"Debug MemAccess"))
   lazy val module = new MemAccessToTLModule(this,num_core_ports)
}

class MemAccessToTLBundle(outer: MemAccessToTL,num_core_ports: Int)(implicit conf: SodorConfiguration,p: Parameters) extends Bundle(){
   val core_ports = Vec(num_core_ports, Flipped(new MemPortIo(data_width = conf.xprlen)) )
   val debug_port = Flipped(new MemPortIo(data_width = conf.xprlen))  
   val tl_data = outer.masterData.bundleOut
   val tl_instr = outer.masterInstr.bundleOut 
   val tl_debug = outer.masterDebug.bundleOut  
}

class MemAccessToTLModule(outer: MemAccessToTL,num_core_ports: Int, num_bytes: Int = (1 << 21))(implicit conf: SodorConfiguration,p: Parameters) extends LazyModuleImp(outer) with Common.MemoryOpConstants
{
   val io = new MemAccessToTLBundle(outer,num_core_ports)

   val edge_debug = outer.masterDebug.edgesOut.head
   val tl_debug = io.tl_debug.head
   val edge_data = outer.masterData.edgesOut.head
   val tl_data = io.tl_data.head
   val edge_instr = outer.masterInstr.edgesOut.head
   val tl_instr = io.tl_instr.head

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
   val resp_datai = tl_data.d.bits.data

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
   tl_data.a.valid := false.B
   tl_instr.a.valid := false.B
   

   // DEBUG PORT-------
   io.debug_port.req.ready := tl_debug.a.ready // for now, no back pressure
   io.debug_port.resp.valid := tl_debug.d.valid //Reg(next = io.debug_port.req.valid && io.debug_port.req.bits.fcn === M_XRD)
   // asynchronous read
   tl_debug.a.bits.address := io.debug_port.req.bits.addr
   tl_debug.a.valid := io.debug_port.req.valid
   printf("MMTL: AV:%x %x AR:%x AA:%x AD:%x RR:%x RD:%x\n",tl_debug.a.valid,io.debug_port.req.valid,tl_debug.a.ready,tl_debug.a.bits.address,tl_debug.a.bits.data
      ,tl_debug.d.valid,tl_debug.d.bits.data)
   tl_debug.d.ready := true.B
   tl_debug.a.bits.size := 2.U
   tl_debug.a.bits.mask := 15.U
   io.debug_port.resp.bits.data := tl_debug.d.bits.data
   tl_debug.a.bits.opcode := Mux((io.debug_port.req.bits.fcn === M_XWR),1.U,4.U)
   when (io.debug_port.req.valid && io.debug_port.req.bits.fcn === M_XWR)
   {
      tl_debug.a.bits.address := io.debug_port.req.bits.addr
      tl_debug.a.bits.data := io.debug_port.req.bits.data 
   } 

}