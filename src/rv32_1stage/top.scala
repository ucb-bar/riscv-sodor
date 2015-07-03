package Sodor

import Chisel._
import Node._
import Constants._
import Common._
import Common.Util._
import ReferenceChipBackend._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap


object ReferenceChipBackend {
  val initMap = new HashMap[Module, Bool]()
}

class TopIO() extends Bundle  {
  val debug_stats_csr = Bool(OUTPUT)
  val htif  = new Common.HTIFIO()
}

class Top extends Module 
{
   val io = new TopIO()

   implicit val sodor_conf = SodorConfiguration()

   val reset_signal = Reg(next=Reg(next=io.htif.reset))
   val tile = Module(new SodorTile)
  
   tile.io.host.reset := reset_signal
   tile.io.host.id := UInt(0,1)
   tile.io.host.csr_req <> Queue(io.htif.csr_req)
   io.htif.csr_rep <> Queue(tile.io.host.csr_rep)
   tile.io.host.ipi_rep <> Queue(io.htif.ipi_rep)
   io.htif.ipi_req <> Queue(tile.io.host.ipi_req)

   tile.io.host.mem_req <> Queue(io.htif.mem_req)
   io.htif.mem_rep <> tile.io.host.mem_rep

   io.debug_stats_csr := Reg(next=tile.io.host.debug_stats_csr)
}

object elaborate {
  def main(args: Array[String]): Unit = {
    chiselMain(args, () => Module(new Top()))
  }
}
