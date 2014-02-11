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
  val debug_stats_pcr = Bool(OUTPUT)
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
   tile.io.host.pcr_req <> Queue(io.htif.pcr_req)
   printf("pcr_rep.bits = %d, pcr_req.addr = 0x%x\n", io.htif.pcr_rep.bits, io.htif.pcr_req.bits.addr)
   io.htif.pcr_rep <> Queue(tile.io.host.pcr_rep)

   tile.io.host.mem_req <> Queue(io.htif.mem_req)
   io.htif.mem_rep <> tile.io.host.mem_rep

   io.debug_stats_pcr := Reg(next=tile.io.host.debug_stats_pcr)
}

object elaborate {
  def main(args: Array[String]): Unit = {
    chiselMain(args, () => Module(new Top()))
  }
}
