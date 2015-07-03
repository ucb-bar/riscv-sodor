package Common

import Chisel._
import Node._
import Constants._
import Util._


class CSRReq(addr_width: Int) extends Bundle
{
   val rw = Bool()
   val addr = Bits(width = addr_width)
   val data = Bits(width = 64)
   override def clone = { new CSRReq(addr_width).asInstanceOf[this.type] }
}


class HTIFIO() extends Bundle
{
   val reset = Bool(INPUT)
   val debug_stats_csr = Bool(OUTPUT)
   val id = UInt(INPUT, 1)
   val csr_req = Decoupled(new CSRReq(addr_width = 12)).flip
   val csr_rep = Decoupled(Bits(width = 64))
   // inter-processor interrupts. Not really necessary for Sodor.
   val ipi_req = Decoupled(Bits(width = 1))
   val ipi_rep = Decoupled(Bool()).flip
   
   val mem_req = Decoupled(new CSRReq(addr_width = 64)).flip
   val mem_rep = new ValidIO(Bits(width = 64))
}


class SCRIO extends Bundle
{
   val n = 64
   val rdata = Vec.fill(n) { Bits(INPUT, 64) }
   val wen = Bool(OUTPUT)
   val waddr = UInt(OUTPUT, log2Up(n))
   val wdata = Bits(OUTPUT, 64)
}
