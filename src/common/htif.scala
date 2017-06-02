package Common

import chisel3._
import chisel3.util._
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import Constants._
import Util._


class CSRReq(addr_width: Int) extends Bundle
{
   val rw = Output(Bool())
   val addr = Output(UInt(addr_width.W))
   val data = Output(UInt(64.W))
   override def cloneType = { new CSRReq(addr_width).asInstanceOf[this.type] }
}


class HTIFIO() extends Bundle
{
   val reset = Input(Bool())
   val debug_stats_csr = Output(Bool())
   val id = Input(UInt(1.W))
   val csr_req = Flipped(Decoupled(new CSRReq(addr_width = 12)))
   val csr_rep = Decoupled(Output(UInt(64.W)))
   // inter-processor interrupts. Not really necessary for Sodor.
   val ipi_req = Decoupled(Input(UInt(1.W)))
   val ipi_rep = Flipped(Decoupled(Output(Bool())))
   
   val mem_req = Flipped(Decoupled(new CSRReq(addr_width = 64)))
   val mem_rep = new ValidIO(Output(UInt(64.W)))
}


class SCRIO extends Bundle
{
   val n = 64
   val rdata = Vec.fill(n) { Input(UInt(64)) }
   val wen = Output(Bool())
   val waddr = Output(UInt(log2Ceil(n)))
   val wdata = Output(UInt(64))
}
